# user-client: User Domain External Dubbo Contract

`user-client` is the **API package that user-service exposes to other services**. It contains only interfaces and DTOs, with no implementations, Spring components, MyBatis Mappers or database dependencies.

Other services (gateway, order-service, and any new business services you add) can call user-domain capabilities over Dubbo by depending only on this module, without pulling the entire user implementation into their own process.

## 1. What It Contains

### 1.1 Dubbo Interfaces

| Interface | Provider | Purpose |
| --- | --- | --- |
| `PermissionDubboClient` | `PermissionDubboServiceImpl` in `user-pc` | Gateway loads authorization rules and whitelists; queries user roles/permissions |
| `UserDubboClient` | `UserDubboServiceImpl` in `user-pc` | Looks up backend username/nickname by ID |
| `AppUserDubboClient` | `AppUserDubboServiceImpl` in `user-app` | Looks up App user nickname by ID (avoids ID mixing with backend users) |
| `DataScopeDubboClient` | `DataScopeDubboServiceImpl` in `user-pc` | Resolves the data scope of backend users (all/department/self only), for order and other business services to perform row-level filtering |

Methods of `PermissionDubboClient`:

```java
UserInfoDTO getUserInfoByUsername(String username);
UserInfoDTO getUserInfoById(Integer id);
boolean hasPermission(String username, String permission);
boolean hasRole(String username, String role);
List<String> getUserRoles(String username);
List<String> getUserPermissions(String username);
List<ApiPermissionRuleDTO> listApiPermissionRules();
List<ApiPermissionRuleDTO> listEnabledWhitelists();
```

### 1.2 DTOs

| DTO | Fields |
| --- | --- |
| `UserInfoDTO` | `id`, `name`, `age`, `address`, `gender`, `roles`, `permissions` |
| `ApiPermissionRuleDTO` | `code`, `method`, `path` |
| `DataScopeDTO` | `all`, `deptIds`, `selfOnly`, `userId` |

`ApiPermissionRuleDTO` is used for two kinds of data at the same time:

- `sys_api_resource`: `code` is the permission code; the gateway validates that permission when it matches;
- `sys_api_whitelist`: `code` is `null`; the gateway lets the request through directly when it matches.

## 2. Usage

### 2.1 Consumer: Reference the Contract and Declare `@DubboReference`

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
</dependency>
```

```java
@Service
public class OrderLocalServiceImpl implements OrderLocalService {

    @DubboReference(check = false, timeout = 3000)
    private UserDubboClient userDubboClient;

    public OrderDTO getOrderByUserId(Long userId) {
        String userName = userDubboClient.getUserNameById(userId);
        return new OrderDTO(1, "trade-" + userId + "-" + userName);
    }
}
```

- `check = false`: does not require the provider to be ready at startup, avoiding startup-order coupling.
- `timeout = 3000`: sets an explicit timeout for the RPC, avoiding long occupation of request threads.
- In production, consider adding `retries = 0/1`, `cluster = "failfast"` and similar policies, depending on business tolerance.

### 2.2 Provider: Implement the Interface and Expose It with `@DubboService`

```java
@DubboService
@RequiredArgsConstructor
public class UserDubboServiceImpl implements UserDubboClient {

    private final UserService userService;

    @Override
    public String getUserNameById(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userService.get(userId);
        return user.getNickname() == null ? user.getUsername() : user.getNickname();
    }
}
```

Implementation classes go in `user-pc` / `user-app`, **not in `user-client`**. `user-client` has only Lombok in its dependencies; putting an implementation there would immediately introduce heavyweight dependencies such as Spring and MyBatis, breaking its "contract package" role.

### 2.3 Gateway Usage

```java
@DubboReference(check = false, timeout = 3000)
private PermissionDubboClient permissionDubboClient;

// Scheduled refresh
List<ApiPermissionRuleDTO> rules = permissionDubboClient.listApiPermissionRules();
List<ApiPermissionRuleDTO> whitelists = permissionDubboClient.listEnabledWhitelists();
```

gateway is a WebFlux application and cannot include `spring-boot-starter-web`. When referencing `user-client`, `gateway-service/pom.xml` excludes the MVC dependency:

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

> `user-client` itself currently has no Spring Web dependency; if you add dependencies in the future, be sure to keep the "contract module free of framework dependencies", otherwise it will pollute all consumers.

## 3. Add a Contract Capability

Take "batch query backend usernames" as an example:

1. **Change the interface** (`user-client`):

```java
public interface UserDubboClient {
    String getUserNameById(Long userId);

    List<String> listUserNamesByIds(List<Long> userIds);
}
```

2. **Change the DTO** (if needed): add `UserBriefDTO implements Serializable`; do not return the `SysUser` entity directly.

3. **Write the implementation** (`user-pc`):

```java
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {
    @Override
    public List<String> listUserNamesByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userService.listByIds(userIds).stream()
                .map(u -> u.getNickname() == null ? u.getUsername() : u.getNickname())
                .toList();
    }
}
```

4. **Consumers upgrade the dependency** and recompile.

> Adding a method to an interface is a **binary-incompatible change**: old provider instances do not implement the new method, so consumer calls will fail. During a canary release, publish the provider first, then the consumer; alternatively, create a new interface version instead of changing the old interface directly.

## 4. Pitfalls

### 4.1 Referencing `user-pc` Inside `user-client`

```xml
<!-- Wrong: the contract package depends on the implementation package in reverse -->
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-pc</artifactId>
</dependency>
```

Consequence: every service that only wants the API is dragged into implementation dependencies such as MyBatis, Spring Security and Sa-Token, and may even form a `user-pc → user-client → user-pc` cycle.

### 4.2 DTOs Not Implementing `Serializable`

Dubbo uses serialization to transmit objects by default. DTOs must:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
}
```

Also keep a no-arg constructor to avoid deserialization failures.

### 4.3 Returning Database Entities Directly

```java
// Not recommended
UserInfoDTO getUserInfoById(Integer id);
// Internal toUserInfo(SysUser) is fine, but do not expose SysUser in the contract
```

Entity fields change with the table structure and may contain sensitive fields such as password hashes and status. Contract DTOs should contain only the minimal fields the caller needs.

### 4.4 Passing an `app_user` ID to `UserDubboClient`

The user IDs of the two domains are not the same set:

```java
// Wrong: using an App user ID to query sys_user may find a backend user with the same ID
userDubboClient.getUserNameById(appUserId);

// Correct
appUserDubboClient.getNicknameById(appUserId);
```

### 4.5 Calling Dubbo Synchronously in the Gateway Event Loop

`PermissionDubboClient` is used in the gateway only for **scheduled rule refresh** (`@Scheduled` + a dedicated thread pool), not on the authorization path of every request. Authorization reads the token session in Redis and the in-memory rule snapshot.

Do not write "synchronously query permissions via `@DubboReference` on every request" in new code; that would stall the Netty event loop. When user information is needed, prefer request-header/context propagation or asynchronous caching.

### 4.6 Assuming `check = false` Means No Errors

`check = false` only affects whether the provider's existence is checked **at startup**. If the provider is unavailable at call time, `RpcException` is still thrown (timeout/no provider). Consumers must decide whether to degrade based on the business:

```java
try {
    return permissionDubboClient.getUserInfoById(userId);
} catch (Exception e) {
    log.warn("Failed to get user permissions userId={}", userId, e);
    return null; // or use a local cache
}
```

### 4.7 Data Scope Calls Must Not Degrade to "All"

`DataScopeDubboClient.resolve(userId)` is used for row-level data permissions. If a call failure degrades to `all`, that is equivalent to unauthorized access, so the business side must fail closed:

```java
try {
    dto = dataScopeDubboClient.resolve(userId);
} catch (Exception e) {
    throw new DataScopeException("Failed to resolve data scope userId=" + userId, e);
}
```

`DataScopeDTO`'s `userId` is `sys_user.id`; do not pass App users in (the two ID sets are not interchangeable).

## 5. Dependency Usage

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
</dependency>
```

Applicable to: gateway, order-service, and any business service that needs to call user-domain capabilities. **user-service itself does not need to reference it to "call itself"**, but `user-pc` / `user-app` need it to implement the contracts (`implements PermissionDubboClient`, etc.).
