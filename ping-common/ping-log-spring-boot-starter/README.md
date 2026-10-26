# ping-log-spring-boot-starter：业务链路 ID

这个 starter 为每个 HTTP 请求生成/继承一个业务链路 ID（`bizTid`），写入 MDC，并通过响应头和 Feign 往下游传递。配合 Dubbo starter，可以串起“HTTP → Dubbo → HTTP”的完整业务日志。

它只做**业务链路标识**，不是完整的分布式追踪（没有 span、采样、耗时统计）；需要 APM 时另接 OpenTelemetry 等方案。

## 1. 包含什么

| 类 | 作用 |
| --- | --- |
| `trace.BizTraceIdFilter` | servlet Filter：读取/生成 `X-Biz-Tid`，写 MDC 和响应头 |
| `config.BizTraceAutoConfiguration` | 注册 `BizTraceIdGenerator` 和过滤器 |
| `config.BizTraceFeignAutoConfiguration` | 引入 Feign 时，把 `bizTid` 写进请求头 |

自动配置条件：

```java
@ConditionalOnWebApplication(type = SERVLET)
```

所以网关（WebFlux）不会加载它；网关使用自己的 `TraceIdGlobalFilter`（`X-Trace-Id` / `X-Request-Id`）。

## 2. 工作流程

```
请求进入
  → BizTraceIdFilter
      ├─ 读请求头 X-Biz-Tid
      ├─ 没有则生成：优先 DistributedIdGenerator.nextId()，否则 UUID（无横线）
      ├─ MDC.put("bizTid", value)
      ├─ response.setHeader("X-Biz-Tid", value)
      └─ 继续过滤链
请求结束
  → MDC.remove("bizTid")
```

生成逻辑：

```java
// BizTraceAutoConfiguration
DistributedIdGenerator distributed = ...;
if (distributed != null) {
    return distributed::nextId;   // 雪花 ID
}
return () -> UUID.randomUUID().toString().replace("-", "");
```

如果同时引入 `ping-id-spring-boot-starter`，`bizTid` 就是雪花 ID；否则是 32 位 UUID。

## 3. 日志配置

`logback.xml` 中加：

```xml
<property name="FORMAT"
          value="%d{yyyy-MM-dd'T'HH:mm:ss.SSS} [%thread] %level [bizTid:%X{bizTid}] %logger - %msg%n"/>
```

输出示例：

```
2026-10-24T10:15:30.123 [http-nio-8099-exec-1] INFO [bizTid:1845321092837465600] c.p.u.p.c.AuthController - 登录成功
```

本仓库的 `user-service` 和 `order-service` 都已配置该 pattern。

## 4. 跨服务透传

### 4.1 HTTP（Feign）

引入 Feign 后自动注册：

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

下游服务的 `BizTraceIdFilter` 会读取同一个 `X-Biz-Tid`，实现链路延续。

### 4.2 Dubbo

由 `ping-dubbo-spring-boot-starter` 的 `BizTraceConsumerFilter` / `BizTraceProviderFilter` 透传：

```
MDC bizTid → attachment ping-biz-tid → 提供方 MDC bizTid
```

### 4.3 手工携带

```bash
curl -H 'X-Biz-Tid: my-trace-001' http://localhost:9999/user/pc/auth/me
```

响应头会回写同一个值，方便前端/网关日志关联。

## 5. 常用代码

```java
// 当前业务链路 ID
String bizTid = MDC.get("bizTid");

// 日志（logback pattern 已包含时不需要手动拼）
log.info("[trace] bizTid={}", MDC.get("bizTid"));
```

## 6. 常见坑

### 6.1 外部可以伪造 `X-Biz-Tid`

过滤器直接信任请求头，没有格式校验和签名。恶意客户端可以传任意值，造成：

- 日志里出现超长/特殊字符；
- 伪造链路混淆排查。

如果对外暴露，建议在网关统一生成并覆盖该头，而不是透传客户端的值。当前网关的 `TraceIdGlobalFilter` 使用独立的 `X-Trace-Id`，不会覆盖 `X-Biz-Tid`。

### 6.2 异步线程里 MDC 丢失

MDC 是 ThreadLocal。`@Async` 或线程池任务需要 `TaskDecorator` 复制 MDC：

- `user-service` 的 `AsyncExecutorConfig` 已配置；
- `order-service` 的 `AsyncExecutorConfig` 已配置；
- 自己 `new Thread` / 原生线程池需要手动处理。

### 6.3 定时任务没有 `bizTid`

`BizTraceIdFilter` 只处理 HTTP 请求。定时任务、消息消费、启动初始化没有 `bizTid`，日志中该字段为空。需要时自己生成：

```java
String bizTid = UUID.randomUUID().toString().replace("-", "");
MDC.put("bizTid", bizTid);
try {
    // ...
} finally {
    MDC.remove("bizTid");
}
```

### 6.4 网关日志没有 `bizTid`

网关是 WebFlux，本 starter 不生效。网关的 `TraceIdGlobalFilter` 生成的是 `X-Trace-Id` / `X-Request-Id`，与 `bizTid` 是两套标识。需要统一时，可以在网关把 `X-Trace-Id` 同时写入 `X-Biz-Tid`，或在日志采集层做关联。

### 6.5 以为它是 SkyWalking/Zipkin

它只生成一个 ID 并透传，不采集 span、不计算耗时、不上报。排查跨服务性能问题仍需要 APM 或 OpenTelemetry。

### 6.6 响应头暴露内部 ID

`X-Biz-Tid` 会回写到响应头。如果 ID 由雪花算法生成，可能暴露时间信息（取决于 epoch 和位数）。对安全敏感的场景，可以改用 UUID 或不在响应头返回。

## 7. 依赖方式

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-log-spring-boot-starter</artifactId>
</dependency>
```

通常与 `ping-id-spring-boot-starter` 一起使用，让 `bizTid` 是数值型雪花 ID；不引 ID starter 时自动退化为 UUID。
