# ping-common: Backend shared components

`ping-common` is the "foundation" shared by all backend services, split into two categories:

1. **`ping-core`**: pure models and SPI, does not depend on Spring Boot, and can be safely referenced by any module;
2. **Five Spring Boot Starters**: turn security, Dubbo propagation, log tracing, distributed ID, and data permissions into auto-configuration that takes effect as soon as the dependency is added.

## 1. Module overview

| Module | Type | Purpose | Main consumers |
| --- | --- | --- | --- |
| [`ping-core`](ping-core/README.en.md) | Plain jar | Unified response, pagination, user context, API policy annotations, `StpAppUtil`, SPI interfaces | All modules |
| [`ping-web-spring-boot-starter`](ping-web-spring-boot-starter/README.en.md) | starter | Sa-Token login state bridging, `@PreAuthorize`, annotation policy scanning, unified exceptions | user-service, order-service |
| [`ping-dubbo-spring-boot-starter`](ping-dubbo-spring-boot-starter/README.en.md) | starter | User context / `bizTid` propagation through Dubbo attachments | user-service, order-service |
| [`ping-log-spring-boot-starter`](ping-log-spring-boot-starter/README.en.md) | starter | `X-Biz-Tid` generation, MDC writing, Feign propagation | user-service, order-service |
| [`ping-id-spring-boot-starter`](ping-id-spring-boot-starter/README.en.md) | starter | Built-in Snowflake distributed ID | user-service, order-service |
| [`ping-data-scope-spring-boot-starter`](ping-data-scope-spring-boot-starter/README.en.md) | starter | `@DataScope` + MyBatis-Plus interceptor, automatically rewrites SQL according to role data scope | user-service (backend data tables) |

Versions are managed uniformly by `<dependencyManagement>` in the root `pom.xml`; submodules omit version numbers when referencing them.

## 2. Dependencies

```
ping-core
   ▲
   ├── ping-web-spring-boot-starter
   ├── ping-dubbo-spring-boot-starter
   ├── ping-log-spring-boot-starter
   ├── ping-id-spring-boot-starter
   └── ping-data-scope-spring-boot-starter
```

All five starters depend on `ping-core` and have no dependencies among themselves. Business modules combine them as needed:

- Regular servlet business services: `ping-web` + `ping-log` + `ping-id` (add `ping-dubbo` when RPC is needed, and `ping-data-scope` when row-level data permissions are needed);
- Gateway (WebFlux): include only `ping-core`, and must **not** include `ping-web-spring-boot-starter` (it pulls in Spring MVC and servlet filters).

## 3. How starters take effect

All five starters use the Spring Boot 3+/4 auto-configuration mechanism:

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

For example, `ping-web-spring-boot-starter`:

```
com.ping.web.config.CommonSecurityAutoConfiguration
com.ping.web.config.AnnotatedApiAutoConfiguration
```

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, StpUtil.class})
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Import(GlobalExceptionHandler.class)
public class CommonSecurityAutoConfiguration { ... }
```

So business modules do not need to write `@ComponentScan("com.ping.web")`, nor add any annotations to the startup class; adding the dependency is enough.

> This repository does not use `spring.factories` (deprecated since Spring Boot 3). When adding a new starter, use `AutoConfiguration.imports`.

## 4. Design conventions

### 4.1 Auto-configuration should be "overridable"

Most beans in shared starters carry:

```java
@ConditionalOnMissingBean
```

Business modules can define their own beans of the same type to override the default implementations. For example, a custom `PasswordEncoder`, a custom `PermissionProvider`, or a custom `TaskDecorator`.

### 4.2 Strict separation of servlet / reactive

`ping-web-spring-boot-starter` and `ping-log-spring-boot-starter` both carry:

```java
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
```

The gateway is WebFlux and must never load these configurations:

- `SaTokenAuthFilter` is a servlet `OncePerRequestFilter`;
- `SecurityFilterChain` conflicts with Gateway's WebFlux security chain;
- `BizTraceIdFilter` is also servlet-based.

The gateway's trace ID is handled by its own `TraceIdGlobalFilter`, and it includes only `ping-core`.

### 4.3 `ping-core` does not depend on Spring

`ping-core`'s only dependencies are Sa-Token core, TransmittableThreadLocal, and Lombok. That is what allows it to be referenced by the gateway (WebFlux), regular services (servlet), and even non-web batch jobs.

If a class needs `@Component`, `@Bean`, or `@ConfigurationProperties`, it does not belong in `ping-core`; it should go into the corresponding starter.

### 4.4 Failure strategy must be explicit

Shared components usually have two choices:

- **fail-open**: allow when the component is unavailable (e.g. `SaTokenAuthFilter` catches exceptions and does not inject authentication, leaving it to the subsequent `@PreAuthorize`);
- **fail-closed**: deny when the component is unavailable (e.g. the gateway denies by default when the authorization rules are empty).

When writing shared components, clearly state in the class comment which one is chosen, so callers do not misjudge the security boundary.

## 5. Adding a new starter

1. Create a module under `ping-common`, and add `ping-core` and the required dependencies to its `pom.xml`;
2. Write the `@AutoConfiguration` class, preferring `@ConditionalOnClass` / `@ConditionalOnWebApplication` / `@ConditionalOnMissingBean`;
3. Register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`;
4. Register it in `<dependencyManagement>` and `<modules>` of the root `pom.xml`;
5. Add the dependency in the consumer's `pom.xml` without a version;
6. Write a README explaining "what happens after adding it" and "how to override the default behavior".

## 6. Pitfalls

### 6.1 Gateway includes the web starter

Symptoms:

```
Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway
```

Or all requests return 404 after startup. The cause is that `ping-web-spring-boot-starter` transitively brings in `spring-boot-starter-web`, and the servlet container conflicts with WebFlux.

Solution: the gateway should only depend on `ping-core` and `user-client`, excluding `spring-boot-starter-web` if necessary.

### 6.2 Business module lacks Redis, annotations do not take effect

Both `AnnotatedApiScanner` and `SaTokenAuthFilter` depend on `StringRedisTemplate`. Without Redis:

- Annotation scanning does not write to Redis → the gateway cannot see the policies;
- Token session reads fail → permissions are empty.

Services that need annotation policies must have Spring MVC, Redis, and Sa-Token at the same time.

### 6.3 Custom bean does not override successfully

The default beans in shared starters usually carry `@ConditionalOnMissingBean`, but matching is **by type**. If your custom bean has a different type (e.g. implementation class vs. interface), it will not override. To override, keep the return type consistent with the default bean, or use `@Primary`.

### 6.4 Assuming the starter auto-configures Nacos

The four starters do not include the Nacos dependency or configuration. Service registration and the config center are handled by each service's own `spring-cloud-starter-alibaba-nacos-*` and `application.yml`.

## 7. Submodule documentation

- [ping-core](ping-core/README.en.md)
- [ping-web-spring-boot-starter](ping-web-spring-boot-starter/README.en.md)
- [ping-dubbo-spring-boot-starter](ping-dubbo-spring-boot-starter/README.en.md)
- [ping-log-spring-boot-starter](ping-log-spring-boot-starter/README.en.md)
- [ping-id-spring-boot-starter](ping-id-spring-boot-starter/README.en.md)
