# user-common: User Domain Shared Constants and Enums

`user-common` is the lowest-level and "thinnest" module in `ping-user`. It only contains content that **multiple modules in the user domain need to reference and that produces no business behavior**, avoiding rewriting the same constant in Controllers, Services and Mappers.

## 1. What It Contains

| Class | Purpose |
| --- | --- |
| `com.ping.user.common.constant.UserConstants` | Constants such as data scope, session keys, business rules and Redis key prefixes |
| `com.ping.user.common.enums.UserType` | User identity domain: `PC` (`sys_user`) / `APP` (`app_user`) |

Dependencies:

```
user-common
 ├─ com.ping:ping-core
 ├─ spring-boot-starter-validation
 └─ lombok
```

Note: it **does not depend on** MyBatis, Redis, Spring Security or Dubbo. Any module can safely reference it without being forced to pull in additional runtime dependencies.

## 2. Constants

### 2.1 Data Scope `SCOPE_*`

Corresponds to `sys_role.data_scope`:

| Constant | Value | Meaning |
| --- | --- | --- |
| `SCOPE_ALL` | 1 | All data |
| `SCOPE_DEPT` | 2 | Own department |
| `SCOPE_DEPT_AND_CHILD` | 3 | Own department and sub-departments |
| `SCOPE_SELF` | 4 | Self only |
| `SCOPE_CUSTOM` | 5 | Custom departments (`sys_role_dept`) |

Usage example:

```java
if (Objects.equals(role.getDataScope(), UserConstants.SCOPE_ALL)) {
    // Do not restrict by department
}
```

Multiple roles are unioned; if any role is `admin` or `SCOPE_ALL`, it has the full data scope (the specific logic is in `DataScopeServiceImpl` in `user-pc`).

### 2.2 Session Keys

```java
StpUtil.getTokenSession().set(UserConstants.SESSION_IP, clientIp);
StpUtil.getTokenSession().set(UserConstants.SESSION_USER_AGENT, request.getHeader("User-Agent"));
StpAppUtil.LOGIC.getTokenSession().set(UserConstants.APP_PHONE, user.getPhone());
```

| Constant | Value | Purpose |
| --- | --- | --- |
| `SESSION_IP` | `LOGIN_IP` | Online user list displays the login IP |
| `SESSION_USER_AGENT` | `LOGIN_UA` | Online user list displays the client |
| `APP_PHONE` | `APP_PHONE` | Stores the phone number in the App token session |

> These keys are just strings and are **not** fields of `UserContext`. `UserContext`'s `userId/roles/permissions` are written separately by filters.

### 2.3 Business Rules

| Constant | Value | Description |
| --- | --- | --- |
| `BUILT_IN_ADMIN_ID` | `1L` | Built-in administrator user ID |
| `PROVIDER_PHONE` | `phone` | Phone identity in `app_user_identity.provider` |
| `EXPORT_LIMIT` | `10000` | Maximum number of operation log export records |
| `DETAIL_MAX_LENGTH` | `512` | Maximum audit detail length |

### 2.4 Authentication Redis Key Prefixes

| Constant | Value | Purpose |
| --- | --- | --- |
| `CAPTCHA_KEY_PREFIX` | `ping:security:captcha:` | CAPTCHA answer |
| `LOGIN_FAIL_KEY_PREFIX` | `ping:security:login:fail:` | Login failure count |
| `LOGIN_LOCK_KEY_PREFIX` | `ping:security:login:lock:` | Account lock marker |

These keys are read and written by `user-auth`. In multi-instance deployments they share Redis, so lock state is naturally consistent.

## 3. `UserType`

```java
public enum UserType {
    /** Backend user (sys_user, StpUtil) */
    PC,
    /** Consumer user (app_user, StpLogic("app")) */
    APP
}
```

It is suitable for expressing "which user domain this operation targets", for example:

```java
public void notify(UserType type, Long userId) {
    if (type == UserType.APP) {
        appUserDubboClient.getNicknameById(userId);
    } else {
        userDubboClient.getUserNameById(userId);
    }
}
```

**Do not** treat it as a database field and store it directly: currently `sys_user` / `app_user` are two independent tables with no unified `user_type` column; it is only a Java-level enum.

## 4. When to Add Things Here

Suitable:

- Constants used by more than one submodule;
- Enums with no business logic;
- Stable protocol field names and Redis key prefixes.

Not suitable:

- DTOs / VOs used only by `user-pc` — put them in `user-pc/model`;
- Logic that needs to query the database or Redis — put it in `user-auth` or the corresponding business module;
- External Dubbo contracts — put them in `user-client`;
- Cross-service shared response structures, annotations and contexts — put them in `ping-core`.

Criterion: **If this class were deleted, would multiple modules fail to compile?** If not, it probably does not belong in `user-common`.

## 5. Pitfalls

### 5.1 Adding Business Exceptions/Services Causes Circular Dependencies

```java
// Not recommended
public class UserCommonService {
    private final SysUserMapper mapper; // user-common should not depend on MyBatis / user-pc
}
```

Once `user-common` depends on a Mapper, both `user-pc` and `user-app` are forced to introduce database dependencies, and it easily forms a cycle with `user-client`.

### 5.2 Changing Constant Values Affects Existing Data

`SCOPE_*` values are bound to `sys_role.data_scope`, frontend dropdown options and historical SQL. Changing a value is not "changing a constant" but a data migration. Use new values when adding scopes; do not reorder existing values.

### 5.3 Assuming `UserType` Determines the Login Domain

To determine at runtime which domain the current request belongs to, use:

```java
UserContextHolder.isAppUser();  // App context has the "app" role
UserContextHolder.isPcUser();
```

rather than checking `UserType` — the latter is only an enum definition and has nothing to do with the current request.

## 6. Dependency Usage

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-common</artifactId>
</dependency>
```
