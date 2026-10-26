# ping-common：后端公共组件

`ping-common` 是所有后端服务共享的“底座”，分成两类：

1. **`ping-core`**：纯模型与 SPI，不依赖 Spring Boot，任何模块都可以安全引用；
2. **五个 Spring Boot Starter**：把安全、Dubbo 透传、日志链路、分布式 ID、数据权限做成“引依赖即生效”的自动配置。

## 1. 模块一览

| 模块 | 类型 | 作用 | 主要使用方 |
| --- | --- | --- | --- |
| [`ping-core`](ping-core/README.md) | 普通 jar | 统一响应、分页、用户上下文、接口策略注解、`StpAppUtil`、SPI 接口 | 全部模块 |
| [`ping-web-spring-boot-starter`](ping-web-spring-boot-starter/README.md) | starter | Sa-Token 登录态桥接、`@PreAuthorize`、注解策略扫描、统一异常 | user-service、order-service |
| [`ping-dubbo-spring-boot-starter`](ping-dubbo-spring-boot-starter/README.md) | starter | 用户上下文 / `bizTid` 通过 Dubbo attachment 透传 | user-service、order-service |
| [`ping-log-spring-boot-starter`](ping-log-spring-boot-starter/README.md) | starter | `X-Biz-Tid` 生成、MDC 写入、Feign 透传 | user-service、order-service |
| [`ping-id-spring-boot-starter`](ping-id-spring-boot-starter/README.md) | starter | 内置雪花算法分布式 ID | user-service、order-service |
| [`ping-data-scope-spring-boot-starter`](ping-data-scope-spring-boot-starter/README.md) | starter | `@DataScope` + MyBatis-Plus 拦截器，按角色数据范围自动改写 SQL | user-service（后台数据表） |

版本统一由根 `pom.xml` 的 `<dependencyManagement>` 管理，子模块引用时不写版本号。

## 2. 依赖关系

```
ping-core
   ▲
   ├── ping-web-spring-boot-starter
   ├── ping-dubbo-spring-boot-starter
   ├── ping-log-spring-boot-starter
   ├── ping-id-spring-boot-starter
   └── ping-data-scope-spring-boot-starter
```

五个 starter 都依赖 `ping-core`，彼此之间没有依赖。业务模块按需组合：

- 普通 servlet 业务服务：`ping-web` + `ping-log` + `ping-id`（+ 需要 RPC 时加 `ping-dubbo`，需要行级数据权限时加 `ping-data-scope`）；
- 网关（WebFlux）：只引 `ping-core`，**不能**引 `ping-web-spring-boot-starter`（它会引入 Spring MVC 和 servlet 过滤器）。

## 3. Starter 是怎么生效的

五个 starter 都使用 Spring Boot 3+/4 的自动配置机制：

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

例如 `ping-web-spring-boot-starter`：

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

所以业务模块不需要写 `@ComponentScan("com.ping.web")`，也不需要在启动类上加任何注解；引入依赖即可。

> 本仓库没有使用 `spring.factories`（Spring Boot 3 起已废弃），新增 starter 时请用 `AutoConfiguration.imports`。

## 4. 设计约定

### 4.1 自动配置要“可退让”

公共 starter 里的 Bean 大多带：

```java
@ConditionalOnMissingBean
```

业务模块可以定义自己的同类型 Bean 覆盖默认实现。例如自定义 `PasswordEncoder`、自定义 `PermissionProvider`、自定义 `TaskDecorator`。

### 4.2 servlet / reactive 严格区分

`ping-web-spring-boot-starter` 和 `ping-log-spring-boot-starter` 都带：

```java
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
```

gateway 是 WebFlux，绝对不能加载这些配置：

- `SaTokenAuthFilter` 是 servlet `OncePerRequestFilter`；
- `SecurityFilterChain` 会和 Gateway 的 WebFlux 安全链冲突；
- `BizTraceIdFilter` 同样基于 servlet。

网关的链路 ID 由自己的 `TraceIdGlobalFilter` 处理，只引 `ping-core`。

### 4.3 `ping-core` 不依赖 Spring

`ping-core` 的依赖只有 Sa-Token core、TransmittableThreadLocal、Lombok。这样它才能被 gateway（WebFlux）、普通服务（servlet）、甚至非 Web 的批处理任务引用。

如果某个类需要 `@Component`、`@Bean`、`@ConfigurationProperties`，它就不该放在 `ping-core`，应放到对应 starter。

### 4.4 失败策略要明确

公共组件通常有两种选择：

- **fail-open**：组件不可用时放行（例如 `SaTokenAuthFilter` 捕获异常后不注入认证，交给后续 `@PreAuthorize`）；
- **fail-closed**：组件不可用时拒绝（例如网关鉴权规则为空时默认拒绝）。

写公共组件时必须在类注释里写清楚选择了哪种，避免调用方误判安全边界。

## 5. 新增一个 starter

1. 在 `ping-common` 下创建模块，`pom.xml` 引入 `ping-core` 和需要的依赖；
2. 写 `@AutoConfiguration` 类，尽量使用 `@ConditionalOnClass` / `@ConditionalOnWebApplication` / `@ConditionalOnMissingBean`；
3. 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中登记；
4. 在根 `pom.xml` 的 `<dependencyManagement>` 和 `<modules>` 中登记；
5. 在使用方 `pom.xml` 中引依赖，不写版本；
6. 补 README，说明“引入后会发生什么”和“如何覆盖默认行为”。

## 6. 常见坑

### 6.1 网关引了 web starter

症状：

```
Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway
```

或启动后所有请求 404。原因是 `ping-web-spring-boot-starter` 传递了 `spring-boot-starter-web`，servlet 容器和 WebFlux 冲突。

解决：网关只依赖 `ping-core` 和 `user-client`，必要时排除 `spring-boot-starter-web`。

### 6.2 业务模块没引 Redis，注解不生效

`AnnotatedApiScanner` 和 `SaTokenAuthFilter` 都依赖 `StringRedisTemplate`。没有 Redis 时：

- 注解扫描不写 Redis → 网关看不到策略；
- token session 读取失败 → 权限为空。

需要注解策略的服务必须同时具备 Spring MVC、Redis、Sa-Token。

### 6.3 自定义 Bean 没覆盖成功

公共 starter 的默认 Bean 通常带 `@ConditionalOnMissingBean`，但**按类型匹配**。如果你自定义的 Bean 类型不同（例如实现类 vs 接口），不会覆盖。需要覆盖时，返回类型与默认 Bean 保持一致，或用 `@Primary`。

### 6.4 误以为 starter 会自动配置 Nacos

四个 starter 都不包含 Nacos 依赖和配置。服务注册、配置中心由各服务自己的 `spring-cloud-starter-alibaba-nacos-*` 和 `application.yml` 负责。

## 7. 子模块文档

- [ping-core](ping-core/README.md)
- [ping-web-spring-boot-starter](ping-web-spring-boot-starter/README.md)
- [ping-dubbo-spring-boot-starter](ping-dubbo-spring-boot-starter/README.md)
- [ping-log-spring-boot-starter](ping-log-spring-boot-starter/README.md)
- [ping-id-spring-boot-starter](ping-id-spring-boot-starter/README.md)
