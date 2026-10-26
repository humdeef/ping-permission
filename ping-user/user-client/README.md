# user-client：用户域对外 Dubbo 契约

`user-client` 是 **user-service 暴露给其他服务的 API 包**。它只包含接口和 DTO，不包含任何实现、Spring 组件、MyBatis Mapper 或数据库依赖。

其他服务（gateway、order-service，以及你后续新增的业务服务）只依赖这个模块，就能通过 Dubbo 调用用户域能力，而不需要把整套用户实现拖进自己的进程。

## 1. 包含什么

### 1.1 Dubbo 接口

| 接口 | 提供方 | 用途 |
| --- | --- | --- |
| `PermissionDubboClient` | `user-pc` 的 `PermissionDubboServiceImpl` | 网关加载鉴权规则、白名单；查询用户角色/权限 |
| `UserDubboClient` | `user-pc` 的 `UserDubboServiceImpl` | 按 ID 查后台用户名/昵称 |
| `AppUserDubboClient` | `user-app` 的 `AppUserDubboServiceImpl` | 按 ID 查 App 用户昵称（避免与后台用户 ID 串号） |
| `DataScopeDubboClient` | `user-pc` 的 `DataScopeDubboServiceImpl` | 解析后台用户的数据范围（全部/部门/仅本人），供订单等业务服务做行级过滤 |

`PermissionDubboClient` 的方法：

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

### 1.2 DTO

| DTO | 字段 |
| --- | --- |
| `UserInfoDTO` | `id`、`name`、`age`、`address`、`gender`、`roles`、`permissions` |
| `ApiPermissionRuleDTO` | `code`、`method`、`path` |
| `DataScopeDTO` | `all`、`deptIds`、`selfOnly`、`userId` |

`ApiPermissionRuleDTO` 同时用于两类数据：

- `sys_api_resource`：`code` 是权限编码，网关命中后校验该权限；
- `sys_api_whitelist`：`code` 为 `null`，网关命中后直接放行。

## 2. 使用方式

### 2.1 消费方：引用契约并声明 `@DubboReference`

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

- `check = false`：启动时不强制要求提供方已就绪，避免服务启动顺序耦合。
- `timeout = 3000`：给 RPC 设置明确超时，避免请求线程被长期占用。
- 生产环境建议再加 `retries = 0/1`、`cluster = "failfast"` 等策略，按业务容忍度决定。

### 2.2 提供方：实现接口并用 `@DubboService` 暴露

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

实现类放在 `user-pc` / `user-app`，**不要放在 `user-client`**。`user-client` 的依赖里只有 Lombok，放实现会立刻引入 Spring、MyBatis 等重量级依赖，破坏“契约包”定位。

### 2.3 网关的用法

```java
@DubboReference(check = false, timeout = 3000)
private PermissionDubboClient permissionDubboClient;

// 定时刷新
List<ApiPermissionRuleDTO> rules = permissionDubboClient.listApiPermissionRules();
List<ApiPermissionRuleDTO> whitelists = permissionDubboClient.listEnabledWhitelists();
```

gateway 是 WebFlux 应用，不能引入 `spring-boot-starter-web`。`gateway-service/pom.xml` 在引用 `user-client` 时排除了 MVC 依赖：

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

> 当前 `user-client` 本身没有依赖 Spring Web；如果未来新增依赖，务必保持“契约模块零框架依赖”，否则会污染所有消费方。

## 3. 新增一个契约能力

以“批量查询后台用户名”为例：

1. **改接口**（`user-client`）：

```java
public interface UserDubboClient {
    String getUserNameById(Long userId);

    List<String> listUserNamesByIds(List<Long> userIds);
}
```

2. **改 DTO**（如果需要）：新增 `UserBriefDTO implements Serializable`，不要直接返回 `SysUser` 实体。

3. **写实现**（`user-pc`）：

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

4. **消费方升级依赖**，重新编译即可。

> 接口新增方法属于**二进制不兼容变更**：旧的提供方实例没有实现新方法，消费方调用会失败。灰度发布时应先发布提供方，再发布消费方；或者新建一个接口版本，而不是直接改老接口。

## 4. 常见坑

### 4.1 在 `user-client` 里引用 `user-pc`

```xml
<!-- 错误：契约包反向依赖实现包 -->
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-pc</artifactId>
</dependency>
```

后果：所有只想要 API 的服务都会被拖进 MyBatis、Spring Security、Sa-Token 等实现依赖，甚至形成 `user-pc → user-client → user-pc` 的循环。

### 4.2 DTO 没有实现 `Serializable`

Dubbo 默认使用序列化传输对象。DTO 必须：

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

同时建议保留无参构造器，避免反序列化失败。

### 4.3 直接返回数据库实体

```java
// 不推荐
UserInfoDTO getUserInfoById(Integer id);
// 内部 toUserInfo(SysUser) 可以，但不要把 SysUser 暴露到契约里
```

实体字段会随表结构变化，且可能包含密码哈希、状态等敏感字段。契约 DTO 应只包含调用方需要的最小字段。

### 4.4 把 `app_user` 的 ID 传给 `UserDubboClient`

两个域的用户 ID 不是一套：

```java
// 错误：App 用户 ID 拿去查 sys_user，可能查到同 ID 的后台用户
userDubboClient.getUserNameById(appUserId);

// 正确
appUserDubboClient.getNicknameById(appUserId);
```

### 4.5 在网关事件循环里同步调用 Dubbo

`PermissionDubboClient` 在网关只用于**定时刷新规则**（`@Scheduled` + 独立线程池），不在每个请求的鉴权路径上调用。鉴权时读的是 Redis 中的 token session 和内存中的规则快照。

不要在新代码里写“每个请求都 `@DubboReference` 同步查一次权限”，那会把 Netty 事件循环拖死。需要用户信息时，优先用请求头/上下文透传，或异步缓存。

### 4.6 以为 `check = false` 就不会报错

`check = false` 只影响**启动时**是否检查提供方存在。调用时提供方不可用仍会抛 `RpcException`（超时/无提供者）。消费方要按业务决定是否降级：

```java
try {
    return permissionDubboClient.getUserInfoById(userId);
} catch (Exception e) {
    log.warn("获取用户权限失败 userId={}", userId, e);
    return null; // 或走本地缓存
}
```

### 4.7 数据范围调用不能降级为「全部」

`DataScopeDubboClient.resolve(userId)` 用于行级数据权限。调用失败时如果退化成 `all`，等价于越权，所以业务侧必须 fail-closed：

```java
try {
    dto = dataScopeDubboClient.resolve(userId);
} catch (Exception e) {
    throw new DataScopeException("解析数据范围失败 userId=" + userId, e);
}
```

`DataScopeDTO` 的 `userId` 是 `sys_user.id`，App 用户不要传进来（两套 ID 不通用）。

## 5. 依赖方式

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-client</artifactId>
</dependency>
```

适用方：gateway、order-service、任何需要调用用户域的业务服务。**user-service 自己不需要引用它来“调用自己”**，但 `user-pc` / `user-app` 需要它来实现契约（`implements PermissionDubboClient` 等）。
