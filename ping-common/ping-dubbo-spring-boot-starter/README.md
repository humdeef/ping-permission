# ping-dubbo-spring-boot-starter：Dubbo 上下文透传

这个 starter 通过 Dubbo Filter，把两样东西从调用方传到提供方：

1. **用户上下文**：`userId`、角色、权限；
2. **业务链路 ID**：`bizTid`（MDC）。

它不注册注册中心、不配置协议，只提供 4 个 `org.apache.dubbo.rpc.Filter` 实现。

## 1. 包含什么

| Filter | 方向 | 作用 |
| --- | --- | --- |
| `UserContextConsumerFilter` | CONSUMER | 把 `UserContextHolder` 写入 RPC attachment |
| `UserContextProviderFilter` | PROVIDER | 从 attachment 恢复 `UserContextHolder` |
| `BizTraceConsumerFilter` | CONSUMER | 把 MDC 的 `bizTid` 写入 attachment |
| `BizTraceProviderFilter` | PROVIDER | 从 attachment 恢复 MDC |

注册文件：

```
src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter
```

```properties
pingUserContextConsumer=com.ping.dubbo.UserContextConsumerFilter
pingUserContextProvider=com.ping.dubbo.UserContextProviderFilter
pingBizTraceConsumer=com.ping.dubbo.BizTraceConsumerFilter
pingBizTraceProvider=com.ping.dubbo.BizTraceProviderFilter
```

Filter 使用 `@Activate(group = CONSUMER/PROVIDER, order = ...)` 自动激活，业务代码不需要手动挂载。

## 2. 透传的 attachment

| key | 内容 | 写入方 | 读取方 |
| --- | --- | --- | --- |
| `ping-user-id` | 用户 ID | `UserContextConsumerFilter` | `UserContextProviderFilter` |
| `ping-user-roles` | 角色，逗号分隔 | 同上 | 同上 |
| `ping-user-perms` | 权限，逗号分隔 | 同上 | 同上 |
| `ping-biz-tid` | 业务链路 ID | `BizTraceConsumerFilter` | `BizTraceProviderFilter` |

注意：

- **token 不会透传**。只传用户身份和权限快照，不传 Sa-Token token；
- attachment 只在本次 RPC 调用内有效，调用结束会 remove。

## 3. 使用方式

### 3.1 引入依赖

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-dubbo-spring-boot-starter</artifactId>
</dependency>
```

同时需要 Dubbo（业务模块通常通过 `dubbo-spring-boot-starter` / `dubbo-registry-nacos` 引入）。

### 3.2 消费方

```java
@DubboReference(check = false, timeout = 3000)
private UserDubboClient userDubboClient;

public void doSomething() {
    // 前提：当前 HTTP 请求已经由 SaTokenAuthFilter/AppTokenAuthFilter 写入上下文
    String name = userDubboClient.getUserNameById(UserContextHolder.getUserId());
}
```

调用时，`UserContextConsumerFilter` 自动把当前 `UserContext` 放进 attachment。

### 3.3 提供方

```java
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {

    @Override
    public String getUserNameById(Long userId) {
        Long callerId = UserContextHolder.getUserId();          // 调用方用户
        boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
        // ...
    }
}
```

`UserContextProviderFilter` 会在方法执行前恢复上下文，执行后清理。

### 3.4 日志中的 `bizTid`

```java
log.info("[trace] bizTid={}", MDC.get("bizTid"));
```

提供方线程的 MDC 会被恢复为调用方的值；logback pattern 里加 `%X{bizTid}` 即可自动打印：

```
%d{...} [%thread] %level [bizTid:%X{bizTid}] %logger - %msg%n
```

## 4. 常见坑

### 4.1 在提供方里调用 `StpUtil`

Dubbo provider 线程没有 HTTP 请求，**不能**：

```java
Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // 可能抛 NotLoginException
```

正确方式：

- 调用方显式传 `userId`；
- 或用 `UserContextHolder.getUserId()`（依赖本 starter 的 filter）。

即便如此，`UserContext` 只是“调用方身份快照”，不是完整会话；需要校验 token 是否有效、是否被踢下线时，必须在调用方完成。

### 4.2 异步线程里上下文为空

`UserContextConsumerFilter` 读取的是**当前线程**的 `UserContextHolder`。如果 Dubbo 调用发生在异步线程：

```java
// 错误：线程池线程没有上下文
executor.submit(() -> userDubboClient.getUserNameById(userId));
```

需要 TTL 包装：

```java
executor.submit(TtlRunnable.get(() -> userDubboClient.getUserNameById(userId)));
```

或者显式传 userId，不依赖上下文。

### 4.3 以为 token 会一起传过去

不会。提供方拿到的是 `ping-user-id` / roles / perms 三个 attachment。如果提供方需要“当前请求的 token”，必须由调用方显式作为参数传入（通常不建议）。

### 4.4 上下文串线程

Filter 在 `finally` 中清理：

```java
try {
    UserContextHolder.set(context);
    return invoker.invoke(invocation);
} finally {
    UserContextHolder.clear();
}
```

如果你自己写 Filter 或拦截器，也要清理；Dubbo 线程池会复用线程。

### 4.5 `@Activate` 顺序

用户上下文 consumer filter 的 order 是 `-10000`，bizTid consumer filter 是 `-9999`。两者互不依赖，顺序不影响结果。新增 filter 时避免使用相同的 order 造成不确定顺序。

### 4.6 只对 Dubbo 生效

HTTP 调用（Feign、RestTemplate、WebClient）不会被这个 starter 透传。HTTP 链路 ID 由 `ping-log-spring-boot-starter` 的 Feign 拦截器处理；用户上下文需要业务自己加请求头。

### 4.7 提供方没有引 starter

只有**消费方和提供方都引入** `ping-dubbo-spring-boot-starter`，attachment 才会被写入和恢复。只在一端引入时：

- 消费方有、提供方没有 → 提供方读不到 `UserContextHolder`；
- 提供方有、消费方没有 → attachment 为空，提供方上下文为空。

## 5. 依赖方式

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-dubbo-spring-boot-starter</artifactId>
</dependency>
```

适用：任何使用 Dubbo 的 servlet 服务。网关虽然也用 Dubbo（加载鉴权规则），但它的调用发生在定时任务线程，不依赖 HTTP 用户上下文；网关是否引入本 starter 不影响规则加载。
