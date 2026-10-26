# user-common：用户域公共常量与枚举

`user-common` 是 `ping-user` 里最底层、最“薄”的模块。它只放**用户域多个模块都要引用、且不产生业务行为**的内容，避免同一个常量在 Controller、Service、Mapper 里各写一份。

## 1. 包含什么

| 类 | 作用 |
| --- | --- |
| `com.ping.user.common.constant.UserConstants` | 数据范围、会话 key、业务规则、Redis key 前缀等常量 |
| `com.ping.user.common.enums.UserType` | 用户身份域：`PC`（`sys_user`）/ `APP`（`app_user`） |

依赖：

```
user-common
 ├─ com.ping:ping-core
 ├─ spring-boot-starter-validation
 └─ lombok
```

注意：它**不依赖** MyBatis、Redis、Spring Security、Dubbo。任何模块都可以安全引用它，不会被迫引入额外运行时。

## 2. 常量说明

### 2.1 数据范围 `SCOPE_*`

对应 `sys_role.data_scope`：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `SCOPE_ALL` | 1 | 全部数据 |
| `SCOPE_DEPT` | 2 | 本部门 |
| `SCOPE_DEPT_AND_CHILD` | 3 | 本部门及下级 |
| `SCOPE_SELF` | 4 | 仅本人 |
| `SCOPE_CUSTOM` | 5 | 自定义部门（`sys_role_dept`） |

使用示例：

```java
if (Objects.equals(role.getDataScope(), UserConstants.SCOPE_ALL)) {
    // 不限制部门
}
```

多个角色取并集；任一角色是 `admin` 或 `SCOPE_ALL`，就拥有全部数据范围（具体逻辑在 `user-pc` 的 `DataScopeServiceImpl`）。

### 2.2 会话 key

```java
StpUtil.getTokenSession().set(UserConstants.SESSION_IP, clientIp);
StpUtil.getTokenSession().set(UserConstants.SESSION_USER_AGENT, request.getHeader("User-Agent"));
StpAppUtil.LOGIC.getTokenSession().set(UserConstants.APP_PHONE, user.getPhone());
```

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `SESSION_IP` | `LOGIN_IP` | 在线用户列表展示登录 IP |
| `SESSION_USER_AGENT` | `LOGIN_UA` | 在线用户列表展示客户端 |
| `APP_PHONE` | `APP_PHONE` | App token 会话里保存手机号 |

> 这些 key 只是字符串，**不是** `UserContext` 的字段。`UserContext` 的 `userId/roles/permissions` 由过滤器另行写入。

### 2.3 业务规则

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `BUILT_IN_ADMIN_ID` | `1L` | 内置管理员用户 ID |
| `PROVIDER_PHONE` | `phone` | `app_user_identity.provider` 的手机号身份 |
| `EXPORT_LIMIT` | `10000` | 操作日志导出最大条数 |
| `DETAIL_MAX_LENGTH` | `512` | 审计详情最大长度 |

### 2.4 认证 Redis key 前缀

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `CAPTCHA_KEY_PREFIX` | `ping:security:captcha:` | 验证码答案 |
| `LOGIN_FAIL_KEY_PREFIX` | `ping:security:login:fail:` | 登录失败计数 |
| `LOGIN_LOCK_KEY_PREFIX` | `ping:security:login:lock:` | 账号锁定标记 |

这些 key 由 `user-auth` 读写。多实例部署时因为共用 Redis，锁定状态天然一致。

## 3. `UserType`

```java
public enum UserType {
    /** 后台用户（sys_user，StpUtil） */
    PC,
    /** C 端用户（app_user，StpLogic("app")） */
    APP
}
```

它适合用来表达“这个操作面向哪个用户域”，例如：

```java
public void notify(UserType type, Long userId) {
    if (type == UserType.APP) {
        appUserDubboClient.getNicknameById(userId);
    } else {
        userDubboClient.getUserNameById(userId);
    }
}
```

**不要**把它当成数据库字段直接存：目前 `sys_user` / `app_user` 是两张独立的表，没有统一的 `user_type` 列；它只是 Java 层的枚举。

## 4. 什么时候该往这里加东西

适合：

- 两个以上子模块都要用的常量；
- 没有业务逻辑的枚举；
- 稳定的协议字段名、Redis key 前缀。

不适合：

- 只有 `user-pc` 用的 DTO / VO —— 放到 `user-pc/model`；
- 需要查库或 Redis 的逻辑 —— 放到 `user-auth` 或对应业务模块；
- 对外 Dubbo 契约 —— 放到 `user-client`；
- 跨服务通用的响应结构、注解、上下文 —— 放到 `ping-core`。

判断标准：**如果删掉这个类，会不会有多个模块编译失败？** 不会的话，它大概率不该放在 `user-common`。

## 5. 常见坑

### 5.1 把业务异常/服务塞进来导致循环依赖

```java
// 不推荐
public class UserCommonService {
    private final SysUserMapper mapper; // user-common 不该依赖 MyBatis / user-pc
}
```

一旦 `user-common` 依赖了 Mapper，`user-pc` 和 `user-app` 都会被迫引入数据库依赖，且很容易和 `user-client` 形成环。

### 5.2 修改常量值影响存量数据

`SCOPE_*` 的值与 `sys_role.data_scope`、前端下拉选项、历史 SQL 都绑定。改值不是“改个常量”，而是数据迁移。新增范围时用新值，不要重排已有值。

### 5.3 误以为 `UserType` 是登录域判断依据

运行时判断当前请求属于哪个域，用：

```java
UserContextHolder.isAppUser();  // App 上下文带 "app" 角色
UserContextHolder.isPcUser();
```

而不是查 `UserType`——后者只是一个枚举定义，和当前请求无关。

## 6. 依赖方式

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-common</artifactId>
</dependency>
```
