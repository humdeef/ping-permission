# ping-log-spring-boot-starter: Business trace ID

This starter generates/inherits a business trace ID (`bizTid`) for each HTTP request, writes it to the MDC, and propagates it downstream through response headers and Feign. Together with the Dubbo starter, it can string together a complete "HTTP → Dubbo → HTTP" business log.

It only provides a **business trace identifier**, not full distributed tracing (no spans, sampling, or duration statistics); when APM is needed, integrate OpenTelemetry or similar separately.

## 1. What's included

| Class | Purpose |
| --- | --- |
| `trace.BizTraceIdFilter` | servlet Filter: reads/generates `X-Biz-Tid`, writes MDC and response header |
| `config.BizTraceAutoConfiguration` | Registers `BizTraceIdGenerator` and the filter |
| `config.BizTraceFeignAutoConfiguration` | Writes `bizTid` into request headers when Feign is present |

Auto-configuration condition:

```java
@ConditionalOnWebApplication(type = SERVLET)
```

So the gateway (WebFlux) does not load it; the gateway uses its own `TraceIdGlobalFilter` (`X-Trace-Id` / `X-Request-Id`).

## 2. Workflow

```
Request arrives
  → BizTraceIdFilter
      ├─ Read request header X-Biz-Tid
      ├─ If absent, generate: prefer DistributedIdGenerator.nextId(), otherwise UUID (no hyphens)
      ├─ MDC.put("bizTid", value)
      ├─ response.setHeader("X-Biz-Tid", value)
      └─ Continue the filter chain
Request ends
  → MDC.remove("bizTid")
```

Generation logic:

```java
// BizTraceAutoConfiguration
DistributedIdGenerator distributed = ...;
if (distributed != null) {
    return distributed::nextId;   // Snowflake ID
}
return () -> UUID.randomUUID().toString().replace("-", "");
```

If `ping-id-spring-boot-starter` is also included, `bizTid` is a Snowflake ID; otherwise it is a 32-character UUID.

## 3. Log configuration

Add to `logback.xml`:

```xml
<property name="FORMAT"
          value="%d{yyyy-MM-dd'T'HH:mm:ss.SSS} [%thread] %level [bizTid:%X{bizTid}] %logger - %msg%n"/>
```

Output example:

```
2026-10-24T10:15:30.123 [http-nio-8099-exec-1] INFO [bizTid:1845321092837465600] c.p.u.p.c.AuthController - 登录成功
```

`user-service` and `order-service` in this repository are both configured with this pattern.

## 4. Cross-service propagation

### 4.1 HTTP (Feign)

Automatically registered when Feign is present:

```java
@Bean
@ConditionalOnMissingBean(name = "bizTraceIdRequestInterceptor")
public RequestInterceptor bizTraceIdRequestInterceptor() {
    return template -> {
        String bizTid = MDC.get(BizTraceIdFilter.MDC_KEY);
        if (bizTid != null && !bizTid.isBlank()) {
            template.header(BizTraceIdFilter.HEADER, bizTid);
        }
    };
}
```

The downstream service's `BizTraceIdFilter` reads the same `X-Biz-Tid`, continuing the trace.

### 4.2 Dubbo

Propagated by `BizTraceConsumerFilter` / `BizTraceProviderFilter` of `ping-dubbo-spring-boot-starter`:

```
MDC bizTid → attachment ping-biz-tid → provider MDC bizTid
```

### 4.3 Manual carrying

```bash
curl -H 'X-Biz-Tid: my-trace-001' http://localhost:9999/user/pc/auth/me
```

The response header echoes the same value, making it convenient to correlate frontend/gateway logs.

## 5. Common code

```java
// Current business trace ID
String bizTid = MDC.get("bizTid");

// Logging (no need to concatenate manually when the logback pattern already includes it)
log.info("[trace] bizTid={}", MDC.get("bizTid"));
```

## 6. Pitfalls

### 6.1 External clients can forge `X-Biz-Tid`

The filter trusts the request header directly, with no format validation or signature. A malicious client can pass arbitrary values, causing:

- Overlong/special characters in logs;
- Forged traces that confuse troubleshooting.

If exposed externally, it is recommended to generate and override this header uniformly at the gateway rather than propagating the client's value. The current gateway's `TraceIdGlobalFilter` uses a separate `X-Trace-Id` and does not override `X-Biz-Tid`.

### 6.2 MDC lost in async threads

MDC is a ThreadLocal. `@Async` or thread pool tasks need a `TaskDecorator` to copy the MDC:

- `user-service`'s `AsyncExecutorConfig` is already configured;
- `order-service`'s `AsyncExecutorConfig` is already configured;
- `new Thread` / native thread pools need manual handling.

### 6.3 Scheduled tasks have no `bizTid`

`BizTraceIdFilter` only handles HTTP requests. Scheduled tasks, message consumption, and startup initialization have no `bizTid`, so the field is empty in logs. Generate it yourself when needed:

```java
String bizTid = UUID.randomUUID().toString().replace("-", "");
MDC.put("bizTid", bizTid);
try {
    // ...
} finally {
    MDC.remove("bizTid");
}
```

### 6.4 Gateway logs have no `bizTid`

The gateway is WebFlux, and this starter does not take effect there. The gateway's `TraceIdGlobalFilter` generates `X-Trace-Id` / `X-Request-Id`, which are two separate identifier systems from `bizTid`. To unify them, the gateway can write `X-Trace-Id` into `X-Biz-Tid` as well, or correlate them at the log collection layer.

### 6.5 Confusing it with SkyWalking/Zipkin

It only generates one ID and propagates it; it does not collect spans, calculate durations, or report data. Troubleshooting cross-service performance issues still requires APM or OpenTelemetry.

### 6.6 Response headers expose internal IDs

`X-Biz-Tid` is echoed to the response header. If the ID is generated by the Snowflake algorithm, it may expose time information (depending on the epoch and bit count). For security-sensitive scenarios, switch to UUID or do not return it in the response header.

## 7. Adding the dependency

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-log-spring-boot-starter</artifactId>
</dependency>
```

It is usually used together with `ping-id-spring-boot-starter` so that `bizTid` is a numeric Snowflake ID; without the ID starter, it automatically degrades to UUID.
