# ping-dubbo-spring-boot-starter: Dubbo context propagation

This starter uses Dubbo Filters to pass two things from the consumer to the provider:

1. **User context**: `userId`, roles, permissions;
2. **Business trace ID**: `bizTid` (MDC).

It does not register a registry or configure protocols; it only provides four `org.apache.dubbo.rpc.Filter` implementations.

## 1. What's included

| Filter | Direction | Purpose |
| --- | --- | --- |
| `UserContextConsumerFilter` | CONSUMER | Writes `UserContextHolder` into the RPC attachment |
| `UserContextProviderFilter` | PROVIDER | Restores `UserContextHolder` from the attachment |
| `BizTraceConsumerFilter` | CONSUMER | Writes the MDC `bizTid` into the attachment |
| `BizTraceProviderFilter` | PROVIDER | Restores the MDC from the attachment |

Registration file:

```
src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter
```

```properties
pingUserContextConsumer=com.ping.dubbo.UserContextConsumerFilter
pingUserContextProvider=com.ping.dubbo.UserContextProviderFilter
pingBizTraceConsumer=com.ping.dubbo.BizTraceConsumerFilter
pingBizTraceProvider=com.ping.dubbo.BizTraceProviderFilter
```

Filters are auto-activated via `@Activate(group = CONSUMER/PROVIDER, order = ...)`; business code does not need to mount them manually.

## 2. Propagated attachments

| key | Content | Writer | Reader |
| --- | --- | --- | --- |
| `ping-user-id` | User ID | `UserContextConsumerFilter` | `UserContextProviderFilter` |
| `ping-user-roles` | Roles, comma-separated | Same as above | Same as above |
| `ping-user-perms` | Permissions, comma-separated | Same as above | Same as above |
| `ping-biz-tid` | Business trace ID | `BizTraceConsumerFilter` | `BizTraceProviderFilter` |

Notes:

- **The token is not propagated**. Only the user identity and permission snapshot are passed, not the Sa-Token token;
- Attachments are valid only within the current RPC call and are removed when the call ends.

## 3. Usage

### 3.1 Add the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-dubbo-spring-boot-starter</artifactId>
</dependency>
```

Dubbo is also required (business modules usually bring it in via `dubbo-spring-boot-starter` / `dubbo-registry-nacos`).

### 3.2 Consumer

```java
@DubboReference(check = false, timeout = 3000)
private UserDubboClient userDubboClient;

public void doSomething() {
    // Prerequisite: the current HTTP request has already been written into the context by SaTokenAuthFilter/AppTokenAuthFilter
    String name = userDubboClient.getUserNameById(UserContextHolder.getUserId());
}
```

During the call, `UserContextConsumerFilter` automatically puts the current `UserContext` into the attachment.

### 3.3 Provider

```java
@DubboService
public class UserDubboServiceImpl implements UserDubboClient {

    @Override
    public String getUserNameById(Long userId) {
        Long callerId = UserContextHolder.getUserId();          // Caller's user
        boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
        // ...
    }
}
```

`UserContextProviderFilter` restores the context before the method executes and cleans it up afterward.

### 3.4 `bizTid` in logs

```java
log.info("[trace] bizTid={}", MDC.get("bizTid"));
```

The MDC of the provider thread is restored to the caller's value; adding `%X{bizTid}` to the logback pattern prints it automatically:

```
%d{...} [%thread] %level [bizTid:%X{bizTid}] %logger - %msg%n
```

## 4. Pitfalls

### 4.1 Calling `StpUtil` in the provider

Dubbo provider threads have no HTTP request and **must not**:

```java
Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId())); // May throw NotLoginException
```

Correct approach:

- The consumer passes `userId` explicitly;
- Or use `UserContextHolder.getUserId()` (relies on this starter's filter).

Even so, `UserContext` is only a "caller identity snapshot", not a full session; when you need to verify whether the token is valid or has been kicked offline, you must do it on the consumer side.

### 4.2 Context is empty in async threads

`UserContextConsumerFilter` reads the `UserContextHolder` of the **current thread**. If the Dubbo call happens in an async thread:

```java
// Wrong: the thread pool thread has no context
executor.submit(() -> userDubboClient.getUserNameById(userId));
```

TTL wrapping is required:

```java
executor.submit(TtlRunnable.get(() -> userDubboClient.getUserNameById(userId)));
```

Or pass userId explicitly and do not rely on the context.

### 4.3 Assuming the token is passed along

It is not. The provider receives three attachments: `ping-user-id` / roles / perms. If the provider needs "the token of the current request", the consumer must pass it explicitly as a parameter (generally not recommended).

### 4.4 Context leaking across threads

Filters clean up in `finally`:

```java
try {
    UserContextHolder.set(context);
    return invoker.invoke(invocation);
} finally {
    UserContextHolder.clear();
}
```

If you write your own Filter or interceptor, clean up as well; Dubbo thread pools reuse threads.

### 4.5 `@Activate` order

The user context consumer filter has order `-10000`, and the bizTid consumer filter has `-9999`. They do not depend on each other, so the order does not affect the result. When adding a new filter, avoid using the same order, which would cause an indeterminate order.

### 4.6 Only effective for Dubbo

HTTP calls (Feign, RestTemplate, WebClient) are not propagated by this starter. HTTP trace IDs are handled by the Feign interceptor of `ping-log-spring-boot-starter`; user context requires the business to add request headers itself.

### 4.7 Provider does not include the starter

The attachment is written and restored only when **both the consumer and the provider include** `ping-dubbo-spring-boot-starter`. When only one side includes it:

- Consumer has it, provider does not → the provider cannot read `UserContextHolder`;
- Provider has it, consumer does not → the attachment is empty, and the provider context is empty.

## 5. Adding the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-dubbo-spring-boot-starter</artifactId>
</dependency>
```

Applicable to: any servlet service that uses Dubbo. Although the gateway also uses Dubbo (to load authorization rules), its calls happen in scheduled task threads and do not depend on the HTTP user context; whether the gateway includes this starter does not affect rule loading.
