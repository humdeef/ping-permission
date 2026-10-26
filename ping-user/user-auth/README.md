# user-auth：认证原语

`user-auth` 提供**与具体用户表无关**的认证基础能力：密码复杂度、登录失败锁定、算术验证码。`user-pc` 和 `user-app` 都复用它，但各自决定“查哪张表、登录域是什么”。

它不查 MySQL，也不生成 token；只依赖 Redis 和配置。

## 1. 包含什么

| 类 | 作用 |
| --- | --- |
| `config.PingSecurityProperties` | `ping.security.*` 配置绑定：注册开关、默认角色、登录锁定、密码策略、验证码 |
| `security.PasswordPolicy` | 密码复杂度校验（长度 + 字符类别数） |
| `security.LoginAttemptService` / `impl.LoginAttemptServiceImpl` | 登录失败计数、账号锁定、成功后清理 |
| `security.CaptchaService` / `impl.CaptchaServiceImpl` | 一次性算术验证码，答案存 Redis |

依赖：

```
user-auth
 ├─ user-common
 ├─ spring-boot-starter
 ├─ spring-boot-starter-data-redis
 └─ lombok
```

## 2. 配置

```yaml
ping:
  security:
    registration-enabled: true    # 后台是否开放自助注册
    default-role-code: guest      # 注册后绑定的默认角色
    login:
      max-attempts: 5             # 连续失败 5 次后锁定
      lock-minutes: 15
    password:
      min-length: 8
      max-length: 64
      require-categories: 3       # 大写/小写/数字/特殊字符至少 3 类
    captcha:
      enabled: false              # 开启后登录必须携带验证码
      expire-minutes: 2
```

`PingSecurityProperties` 本身只带 `@ConfigurationProperties`，**不会自动注册**。`user-service` 在 `SecurityConfig` 上通过 `@EnableConfigurationProperties(PingSecurityProperties.class)` 绑定；如果你把 `user-auth` 用在别的启动模块，也要自己加这一行。

## 3. 用法

### 3.1 密码复杂度

```java
@RequiredArgsConstructor
public class SomeService {
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder; // 由 ping-web-spring-boot-starter 提供（BCrypt）

    public void register(String rawPassword) {
        passwordPolicy.validate(rawPassword);          // 不符合策略会抛 RuntimeException
        String hash = passwordEncoder.encode(rawPassword);
        // ... 入库
    }
}
```

校验规则：

- 非空；
- 长度在 `min-length` ~ `max-length` 之间；
- 大写字母、小写字母、数字、特殊字符中至少满足 `require-categories` 类。

### 3.2 登录失败锁定

```java
if (loginAttemptService.isLocked(username)) {
    long seconds = loginAttemptService.remainingLockSeconds(username);
    throw new RuntimeException("账号已锁定，请 " + seconds + " 秒后再试");
}

if (passwordMismatch) {
    int remaining = loginAttemptService.recordFailure(username);
    if (remaining <= 0) {
        throw new RuntimeException("登录失败次数过多，账号已锁定");
    }
    throw new RuntimeException("用户名或密码错误，还可尝试 " + remaining + " 次");
}

// 登录成功后
loginAttemptService.clear(username);
```

Redis key：

```
ping:security:login:fail:{username}   # 失败计数
ping:security:login:lock:{username}   # 锁定标记
```

### 3.3 验证码

```java
// 生成
CaptchaService.Captcha captcha = captchaService.create();
// captcha.captchaId() 返回给前端；captcha.question() 例如 "3 + 5 = ?"

// 校验（一次性）
boolean ok = captchaService.verify(captchaId, userInput);
```

答案存放在：

```
ping:security:captcha:{captchaId}
```

校验时会**先删除再比较**，因此同一个验证码无论对错都只能用一次。

## 4. PC 与 App 的复用方式

两个域共用同一套 `PasswordPolicy` / `CaptchaService` / 配置，但登录流程不同：

| | `user-pc` 的 `AuthController` | `user-app` 的 `AppAuthController` |
| --- | --- | --- |
| 登录锁定 | 使用 `LoginAttemptService` | **当前未使用** |
| 验证码 | 按 `ping.security.captcha.enabled` 校验 | **当前未使用** |
| 注册开关 | 检查 `registration-enabled` | **当前未检查** |
| 登录域 | `StpUtil` | `StpAppUtil` |
| 密码哈希 | BCrypt | BCrypt |

如果 App 也要防撞库/发验证码，需要在 `AppAuthServiceImpl` 或 `AppAuthController` 中显式接入，不能指望配置自动生效。

示例（给 App 登录加锁定）：

```java
if (loginAttemptService.isLocked(phone)) {
    throw new RuntimeException("尝试次数过多，请稍后再试");
}
try {
    AppUser user = appAuthService.login(phone, password);
    loginAttemptService.clear(phone);
    StpAppUtil.login(user.getId());
} catch (RuntimeException e) {
    loginAttemptService.recordFailure(phone);
    throw e;
}
```

## 5. 常见坑

### 5.1 只引 `user-auth` 却没有 `PasswordEncoder`

`PasswordPolicy` 只校验明文规则，不负责哈希。`PasswordEncoder` 是 `ping-web-spring-boot-starter` 里的 `CommonSecurityAutoConfiguration` 提供的。只引 `user-auth` 时，要么自己定义 `PasswordEncoder`，要么把 web starter 也引进来。

### 5.2 以为 `registration-enabled=false` 能关闭 App 注册

这个开关只作用于 `user-pc` 的 `AuthController.register`。`user-app` 的注册接口没有读取它；需要关闭时，应在 `AppAuthController.register` 或 `AppAuthServiceImpl` 中加判断。

### 5.3 锁定的粒度是用户名/手机号，不是 IP

`recordFailure(username)` 的 key 直接拼接传入值。PC 登录传的是 `username`，App 若接入则传 `phone`。这意味着：

- 攻击者可以换用户名绕过单账号锁定；
- 同一用户名从多个 IP 尝试会共享计数。

如果需要 IP 维度或验证码升级，需要自行扩展 key 设计。

### 5.4 Redis 不可用时的降级行为

`LoginAttemptServiceImpl` 捕获所有 Redis 异常：

- 查锁定失败 → 当作“未锁定”；
- 记录失败失败 → 返回 `maxAttempts`（不锁定）；
- 验证码写入失败 → 只打日志，`create()` 仍返回题目，但 `verify()` 一定失败。

这是“保证登录入口可用”的取舍：Redis 故障时不会把所有人锁死，但防线也变弱。生产环境应监控 Redis 可用性。

### 5.5 验证码答案被日志/响应泄露

`Captcha` 记录里只有 `captchaId` 和题目，没有答案；答案只在 Redis。不要把 Redis 中的值返回给前端，也不要在日志里打印 `captchaId + answer`。

### 5.6 修改密码策略导致老用户无法登录

`PasswordPolicy` 只在**注册/改密/重置密码**时校验，登录时只做 BCrypt 比对。提高 `min-length` 不会让老用户立刻登录失败；但如果管理员重置了密码，新密码必须满足当前策略。

## 6. 依赖方式

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-auth</artifactId>
</dependency>
```

通常业务模块通过 `user-pc` / `user-app` 间接获得它；只有需要单独复用认证原语时才直接引用。
