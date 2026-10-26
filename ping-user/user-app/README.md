# user-app：C 端用户域

`user-app` 负责**移动端 / C 端用户**：使用独立的 `app_user` 表、独立的 App 登录域（`StpAppUtil`），与后台 `sys_user` 完全隔离。它不负责 PC 端页面，也不参与后台 RBAC 权限。

## 1. 包含什么

| 类 | 作用 |
| --- | --- |
| `controller.AppAuthController` | `/user/app/auth/**` 注册、登录、登出、当前用户 |
| `service.AppAuthService` / `impl.AppAuthServiceImpl` | 手机号注册/登录、密码校验、状态校验 |
| `service.dubbo.AppUserDubboServiceImpl` | 实现 `AppUserDubboClient`，供其他服务查昵称 |
| `entity.AppUser` | `app_user` 表 |
| `entity.AppUserIdentity` | `app_user_identity` 表（手机号/微信/Apple 等身份） |
| `mapper.AppUserMapper` / `AppUserIdentityMapper` | MyBatis-Plus Mapper |
| `model.*` | 注册/登录/响应 DTO |

依赖：

```
user-app
 ├─ user-common
 ├─ user-auth            密码策略
 ├─ user-client          AppUserDubboClient 契约
 ├─ ping-web-spring-boot-starter
 ├─ mybatis-plus
 └─ dubbo
```

## 2. 数据模型

### 2.1 `app_user`

| 字段 | 说明 |
| --- | --- |
| `id` | **雪花 ID，应用层生成**（`IdType.INPUT`），不是自增 |
| `phone` | 手机号，唯一 |
| `password_hash` | BCrypt 哈希，`@JsonIgnore` 不序列化 |
| `nickname` | 昵称，为空时注册默认用手机号 |
| `avatar` / `gender` | 资料 |
| `status` | 0 禁用 / 1 正常 / 2 注销中 |
| `register_source` | 注册来源，目前为 `phone` |
| `last_login_at` | 最后登录时间 |
| `create_time` / `update_time` | 自动填充 |

### 2.2 `app_user_identity`

为未来多身份登录预留：

```
provider    = phone / wechat / apple / alipay
identifier  = 手机号 / OpenID / UnionID
credential  = 第三方凭证
```

当前注册会同时写一条 `provider = phone` 的记录，`credential` 为空串。

> `app_user` 和 `app_user_identity` 的 ID 都由 `DistributedIdGenerator` 生成。它们与 `sys_user.id` 的自增值没有任何关系，禁止交叉使用。

## 3. 接口

| 方法 | 路径 | 策略 | 说明 |
| --- | --- | --- | --- |
| POST | `/user/app/auth/register` | `@PublicApi` | 手机号注册 |
| POST | `/user/app/auth/login` | `@PublicApi` | 手机号 + 密码登录 |
| POST | `/user/app/auth/logout` | `@AppLoginOnlyApi` | 退出 |
| GET | `/user/app/auth/me` | `@AppLoginOnlyApi` | 当前 App 用户 |

### 3.1 注册

```bash
curl -s -X POST http://localhost:9999/user/app/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!","nickname":"小P"}'
```

流程：

1. `PasswordPolicy.validate(password)`；
2. 检查手机号是否已注册；
3. 生成雪花 ID，写入 `app_user`；
4. 写入 `app_user_identity(provider=phone)`；
5. 返回用户信息（不返回密码哈希）。

### 3.2 登录

```bash
curl -s -X POST http://localhost:9999/user/app/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","password":"Abc12345!"}'
```

流程：

1. 按手机号查 `app_user`；
2. BCrypt 校验密码；
3. 校验状态（0 禁用 / 2 注销中 → 403）；
4. 更新 `last_login_at`；
5. `StpAppUtil.login(userId)`；
6. token session 写入 `APP_PHONE`；
7. 返回 token、过期时间、用户信息。

之后的请求：

```bash
curl -s http://localhost:9999/user/app/auth/me \
  -H 'ping-token: Bearer <app-token>'
```

### 3.3 登出/当前用户

```java
@AppLoginOnlyApi
@PostMapping("/logout")
public ApiResponse<Void> logout() {
    StpAppUtil.logout();
    return ApiResponse.ok();
}

@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<AppUserResponse> me() {
    Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
    ...
}
```

## 4. 登录域隔离

`StpAppUtil` 的定义在 `ping-core`：

```java
public final class StpAppUtil {
    public static final String TYPE = "app";
    public static final StpLogic LOGIC = new StpLogic(TYPE);
    // login / isLogin / checkLogin / getLoginId / ...
}
```

隔离效果：

| 场景 | 结果 |
| --- | --- |
| App token 调 `/user/app/**` | 通过 |
| App token 调 `/user/pc/**` | 401（PC 域没有该登录态） |
| PC token 调 `/user/app/**` | 401（App 域没有该登录态） |
| PC token 调 `/order/app/**`（`@AppLoginOnlyApi`） | 401 |
| App token 调 `/order/app/**` | 通过，`UserContextHolder` 带 `app` 角色 |

**登录域由注解决定，不靠 URL 前缀。** 网关看到 `@AppLoginOnlyApi` 就调用 `StpAppUtil.checkLogin()`；看到 `@LoginOnlyApi` 或无注解则走 `StpUtil`。

## 5. 对外 Dubbo：`AppUserDubboClient`

```java
public interface AppUserDubboClient {
    String getNicknameById(Long userId);
}
```

实现：

```java
@DubboService
@RequiredArgsConstructor
public class AppUserDubboServiceImpl implements AppUserDubboClient {
    private final AppAuthService appAuthService;

    @Override
    public String getNicknameById(Long userId) {
        AppUser user = appAuthService.findById(userId);
        if (user == null) return null;
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getPhone()
                : user.getNickname();
    }
}
```

调用方（例如 order-service 的 App 接口）：

```java
@DubboReference(check = false, timeout = 3000)
private AppUserDubboClient appUserDubboClient;

String name = appUserDubboClient.getNicknameById(UserContextHolder.getUserId());
```

为什么不用 `UserDubboClient`？后者查的是 `sys_user`，传 App 用户 ID 会串号。

## 6. 常见坑

### 6.1 用 `StpUtil` 写 App 接口

```java
// 错误
@AppLoginOnlyApi
@GetMapping("/me")
public ApiResponse<?> me() {
    return ApiResponse.ok(StpUtil.getLoginId()); // 取不到 App 登录态
}
```

App 接口统一用 `StpAppUtil`，或者用 `UserContextHolder.getUserId()`（过滤器已按域写入）。

### 6.2 以为 App 注册受 `ping.security.registration-enabled` 控制

当前 `AppAuthController.register` 没有读取该开关，App 注册始终可用。要关闭需要自己加判断：

```java
@PublicApi
@PostMapping("/register")
public ApiResponse<AppUserResponse> register(@RequestBody @Valid AppRegisterRequest req) {
    if (!securityProperties.isRegistrationEnabled()) {
        throw new AccessDeniedException("系统未开放注册");
    }
    ...
}
```

### 6.3 App 登录没有失败锁定和验证码

`AppAuthServiceImpl` 只做密码/状态校验，没有接入 `LoginAttemptService` 和 `CaptchaService`。如果需要防撞库，参考 `user-auth/README.md` 的接入示例。

### 6.4 手机号唯一性只靠应用层判断

注册时先 `selectCount` 再 `insert`，并发下仍可能重复。数据库应有唯一索引；若没有，并发注册会写入重复手机号。`GlobalExceptionHandler` 会把 `DuplicateKeyException` 转成 400，但前提是表上有唯一约束。

### 6.5 `passwordHash` 被序列化

`AppUser` 的 `passwordHash` 标了 `@JsonIgnore`，但如果你新增接口直接返回 `AppUser`，仍建议用 `AppUserResponse`，不要依赖注解兜底。

### 6.6 返回 Long 型 ID 给前端

App 用户 ID 是雪花 ID（19 位），超过 JavaScript 安全整数范围（2^53-1）。`AppLoginResponse.userId` 和 `AppUserResponse.userId` 使用 `String` 返回，避免精度丢失。新增 DTO 时保持这个约定。

### 6.7 App 接口被网关 403「接口未配置权限规则」

检查：

1. Controller 或方法上是否有 `@AppLoginOnlyApi` / `@PublicApi`；
2. 注解扫描是否写入 Redis：`ping:gateway:api:app-login:user-service`；
3. 是否等待了网关 10 秒刷新周期；
4. 是否真的经过网关（9999），而不是直连 8099。

App 接口不需要在 `sys_api_resource` 里登记权限码——注解在权限规则之前生效。

## 7. 新增一个 App 接口

```java
@RestController
@RequestMapping("/user/app/profile")
@AppLoginOnlyApi
@RequiredArgsConstructor
public class AppProfileController {

    @GetMapping("/me")
    public ApiResponse<AppUserResponse> me() {
        Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
        AppUser user = appAuthService.findById(userId);
        ...
    }
}
```

清单：

- [ ] 使用 `/user/app/**` 前缀（便于路由和排查，但不是鉴权依据）；
- [ ] 类或方法加 `@AppLoginOnlyApi`（公开接口用 `@PublicApi`）；
- [ ] 用 `StpAppUtil` 或 `UserContextHolder` 取当前用户；
- [ ] 需要跨服务查 App 用户时用 `AppUserDubboClient`；
- [ ] 返回 DTO 时 Long ID 用 String；
- [ ] 不需要改网关配置或 `sys_api_resource`。

## 8. 依赖方式

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-app</artifactId>
</dependency>
```

通常只有 `user-service` 直接依赖。其他服务需要 App 用户能力时，依赖 `user-client` 并调用 `AppUserDubboClient`。
