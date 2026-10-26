# ping-gateway: API Gateway

`ping-gateway` is the unified backend entry point, with a single startup module [`gateway-service`](gateway-service/README.en.md).

- Based on Spring Cloud Gateway (WebFlux);
- Handles routing, authentication, endpoint-level authorization, CORS, and trace ID;
- Does no business logic and does not connect to the user database;
- Loads authorization rules from user-service via the Dubbo contract in `user-client`.

## 1. Module structure

```
ping-gateway/
├── pom.xml              # Aggregate module, groupId com.ping.gateway
└── gateway-service/     # Startup gateway
```

## 2. Routes

Route configuration lives in Nacos `gateway-service-${APP_ENV}.yml`, not in the repository yml:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
              lower-case-service-id: true
          routes:
            - id: user-pc
              uri: lb://user-service
              order: 1
              predicates:
                - Path=/user/pc/**
            - id: user-app
              uri: lb://user-service
              order: 2
              predicates:
                - Path=/user/app/**
            - id: order-service
              uri: lb://order-service
              order: 3
              predicates:
                - Path=/order/**
```

| Path | Target | Description |
| --- | --- | --- |
| `/user/pc/**` | `lb://user-service` | Admin user endpoints |
| `/user/app/**` | `lb://user-service` | App user endpoints |
| `/order/**` | `lb://order-service` | Order sample service |

## 3. Responsibility boundaries

| Gateway handles | Gateway does not handle |
| --- | --- |
| Route forwarding | Business logic |
| Login state validation (`StpUtil` / `StpAppUtil`) | User data queries (except loading rules via Dubbo) |
| Endpoint-level permission code validation | Data permissions (row-level filtering) |
| Whitelist/annotation strategies | Menu/button permission calculation |
| Path normalization, CORS | Audit log persistence |
| Trace ID injection | Distributed tracing spans |

Services still keep `@PreAuthorize` internally as a second line of defense, preventing direct connections that bypass the gateway.

## 4. Why the gateway does not query the database directly

The gateway is WebFlux, and authorization happens on the Netty event loop. If each request synchronously queries MySQL or calls Dubbo:

- blocks the event loop, and throughput drops sharply;
- user-service jitter would directly bring down the gateway;
- database connections would be exhausted by the gateway.

Current design:

- Authorization rules: pulled via Dubbo at startup + periodically, cached in memory;
- User permissions: written to the token session at login (shared Redis); the gateway only reads Redis;
- Whitelist: pulled periodically, cached in memory.

See [`gateway-service`](gateway-service/README.en.md) for details.
