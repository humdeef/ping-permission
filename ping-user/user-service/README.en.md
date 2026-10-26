# user-service: User Service Startup Module

`user-service` is the only runnable module in `ping-user`. It **contains no business logic**; it is only responsible for assembling `user-common`, `user-auth`, `user-pc`, `user-app`, `user-client`, and the various starters into a Spring Boot application.

## 1. What It Contains

```
user-service/
├── pom.xml
└── src/main/
    ├── java/com/ping/user/
    │   ├── UserServiceApplication.java   # main + @MapperScan
    │   └── config/
    │       ├── AsyncExecutorConfig.java  # tt-AsyncExecutor / auditExecutor + MDC
    │       ├── MyBatisPlusConfig.java    # SqlSessionFactory, pagination, auto-fill
    │       └── SecurityConfig.java       # @EnableAsync/@EnableScheduling + security config binding
    └── resources/
        ├── application.yml               # only Nacos bootstrap configuration
        ├── db/migration/                 # Flyway migrations (V1__init_user_schema.sql)
        └── logback.xml                   # console logs, including bizTid
```

Dependencies (besides business modules):

- `ping-web-spring-boot-starter`: Sa-Token bridging, `@PreAuthorize`, unified exceptions, annotation scanning;
- `ping-dubbo-spring-boot-starter`: user context / bizTid propagation;
- `ping-log-spring-boot-starter`: `X-Biz-Tid`, MDC;
- `ping-id-spring-boot-starter`: snowflake ID;
- Nacos discovery/config, MyBatis-Plus, MySQL, Redis, Sa-Token, Caffeine, Actuator/Prometheus;
- `springdoc-openapi-starter-webmvc-ui`: exposes `/v3/api-docs`; the UI is served centrally by the gateway at `http://localhost:9999/docs`.

## 2. Startup Class

```java
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan({"com.ping.user.pc.mapper", "com.ping.user.app.mapper"})
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

Why does `@MapperScan` include two packages?

- The Mappers of `user-pc` are in `com.ping.user.pc.mapper`;
- The Mappers of `user-app` are in `com.ping.user.app.mapper`.

`MyBatisPlusConfig` also has an identical `@MapperScan`. Duplicating it in both places does not conflict, but when adding a Mapper package, **both places must be updated**; otherwise you will get "Mapper not registered".

## 3. Startup Configuration

`src/main/resources/application.yml` only keeps bootstrap information:

```yaml
spring:
  application:
    name: user-service
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:localhost}:${NACOS_PORT:8848}
        namespace: ${APP_ENV:dev}
      config:
        server-addr: ${spring.cloud.nacos.discovery.server-addr}
  config:
    import:
      - nacos:application-${APP_ENV:dev}.yml
      - nacos:${spring.application.name}-${APP_ENV:dev}.yml
```

Runtime configuration lives in Nacos:

- `application-${APP_ENV}.yml`: Redis, Sa-Token, Dubbo registry, Actuator, graceful shutdown;
- `user-service-${APP_ENV}.yml`: port 8099, data source, MyBatis-Plus, Dubbo provider, `ping.security`, audit retention days.

Missing configuration fails fast; it will not start with "half a set of configuration". On the first Docker startup, it is imported by `init-sql/nacos-mysql.sql`.

## 4. Key Configuration Classes

### 4.1 `SecurityConfig`

```java
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PingSecurityProperties.class)
public class SecurityConfig {
}
```

- `@EnableAsync`: asynchronous writes of audit/login logs;
- `@EnableScheduling`: scheduled tasks such as `DataRetentionJob`;
- `@EnableConfigurationProperties`: binds `ping.security.*`.

Note that `ping-web-spring-boot-starter` already provides `SecurityFilterChain`, `PasswordEncoder`, `SaTokenAuthFilter`, etc.; the `SecurityConfig` here does not redefine the security chain and only handles switches and configuration binding.

### 4.2 `MyBatisPlusConfig`

`SqlSessionFactory` is built manually because of compatibility issues with the auto-configuration order of Spring Boot 4 + MyBatis-Plus:

```java
@Bean
public SqlSessionFactory sqlSessionFactory() throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
    factory.setConfiguration(mybatisConfiguration());
    factory.setPlugins(mybatisPlusInterceptor());
    GlobalConfig globalConfig = new GlobalConfig();
    // When building manually, MetaObjectHandler must be attached explicitly; otherwise create_time/update_time will be NULL
    globalConfig.setMetaObjectHandler(metaObjectHandler());
    factory.setGlobalConfig(globalConfig);
    return factory.getObject();
}
```

Includes:

- `PaginationInnerInterceptor(DbType.MYSQL)`: pagination;
- `MetaObjectHandler`: auto-fill `createTime` / `updateTime`;
- global `id-type: AUTO`.

**Pitfall**: If you define another `SqlSessionFactory` yourself, or set `mapper-locations` incorrectly, you will get `Invalid bound statement (not found)` or auto-fill will stop working.

### 4.3 `AsyncExecutorConfig`

| Bean | Purpose | Threads |
| --- | --- | --- |
| `tt-AsyncExecutor` | Business async | core 2 / max 8 / queue 100 |
| `auditExecutor` | Audit, login logs | core 1 / max 4 / queue 2000 |

Both set a `TaskDecorator` that carries the calling thread's MDC (including `bizTid`) to the worker thread; the rejection policy is `CallerRunsPolicy`, so when the queue is full the calling thread executes the task, avoiding log loss.

> The `TaskDecorator` here only propagates MDC, not `UserContextHolder`. If you need the user context in an async task, wrap it with `TtlRunnable.get(...)`, or refer to the `TtlRunnable` usage in order-service.

## 5. Running

### 5.1 Local

```bash
# Build and install the dependency modules from the root directory first
mvn clean install -DskipTests

# Start user-service (do not add -am, otherwise run executes on the aggregator module and reports "main class not found")
mvn -pl ping-user/user-service spring-boot:run

# The configuration namespace follows APP_ENV (dev) by default; if you need public:
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-user/user-service spring-boot:run

# Option 2: run the jar directly
java -jar ping-user/user-service/target/user-service.jar
```

Default port 8099, management port 8101. Before starting, you need:

- MySQL: database `ping`, with schema and seed data created by this service's Flyway migrations at startup (`src/main/resources/db/migration/`);
- Redis;
- Nacos with `application-dev.yml` and `user-service-dev.yml` present: the `dev` namespace is read by default (following `APP_ENV`); when the configuration is in public, set `NACOS_CONFIG_NAMESPACE=""`.

If local MySQL/Redis are not on 3306/6379, override them with `MYSQL_HOST`, `MYSQL_PORT`, `REDIS_HOST`, `REDIS_PORT`; use `NACOS_HOST` and `NACOS_PORT` for the Nacos address.

> Typical error: `Failed to configure a DataSource`. Look upward for `config[... ] is empty` first; it is usually the wrong Nacos namespace or a missing dataId, not a problem with the database itself.

### 5.2 Docker

```bash
./start.sh                  # build + start all containers
./start.sh logs user-service
./start.sh status
```

Inside containers, MySQL/Redis/Nacos are accessed by service name; environment variables are injected by `docker-compose.yml`.

### 5.3 Switching Environments

```bash
APP_ENV=test mvn -pl ping-user/user-service spring-boot:run
APP_ENV=test ./start.sh
```

`APP_ENV` simultaneously determines:

- The Nacos configuration dataId suffix (`application-test.yml`, `user-service-test.yml`);
- The service discovery namespace (`test`);
- The Dubbo registry namespace (`dubbo-test`).

## 6. Health Check

```bash
curl -s http://localhost:8101/actuator/health
```

Docker Compose uses this endpoint to determine whether `user-service` is ready; the gateway waits until it is healthy before starting, avoiding the situation where `PermissionDubboClient` has no provider and authorization rules are empty when the gateway starts.

## 7. Pitfalls

### 7.1 `Table 'ping.xxx' doesn't exist`

The schema is owned by Flyway migrations (`src/main/resources/db/migration/`); V1 covers all runtime tables (including `sys_login_log`, `sys_operation_log`). If old tables exist but a new one does not, check the migration log first. A leftover `success=0` row must be cleared before Flyway will retry:

```bash
mysql -uroot -p ping -e 'DELETE FROM flyway_schema_history_user WHERE success = 0'
```

To re-run the migrations from an empty database:

```bash
./start.sh clean   # drop the data volume; Flyway recreates the tables on next start
```

### 7.2 Missing Nacos configuration causes startup failure

```
Could not resolve placeholder ... / Config data resource not found
```

Check whether the default namespace contains:

```
application-dev.yml
user-service-dev.yml
```

and whether the suffix of `spring.config.import` matches the dataId.

### 7.3 Service registered to the wrong namespace

`spring.cloud.nacos.discovery.namespace` uses `${APP_ENV:dev}`. If `APP_ENV` is not passed at startup, it defaults to `dev`. When the service does not appear in the Nacos console, first confirm that the selected namespace is `dev`, not `public`.

### 7.4 Dubbo provider not registered

Check the Nacos configuration:

```yaml
dubbo:
  scan:
    base-packages: com.ping.user.pc.service,com.ping.user.app.service
```

Implementations of `@DubboService` must be in these two packages. When the gateway reports "no provider", first look at the Dubbo export information in the user-service startup log.

### 7.5 `@MapperScan` missing a package

When adding a new `com.ping.user.xxx.mapper` package, you must update both:

- The `@MapperScan` in `UserServiceApplication`;
- The `@MapperScan` in `MyBatisPlusConfig`.

Otherwise the Mapper will not be registered, and `NoSuchBeanDefinitionException` will be thrown at startup or on invocation.

### 7.6 No `bizTid` in async tasks

The `TaskDecorator` in `AsyncExecutorConfig` copies MDC, but only if `bizTid` is already in MDC when the task is submitted. If the request does not go through `BizTraceIdFilter` (for example scheduled tasks or message consumption), you need to generate it yourself and put it into MDC.

### 7.7 Docker health check fails after changing the port

The healthcheck in `docker-compose.yml` and `server.port` in the Nacos configuration are two independent configurations. Both must be changed when changing the port; otherwise the container stays unhealthy.

## 8. Dependency Usage

`user-service` is a startup module and is usually not packaged as a library for other modules to depend on. When other services need user capabilities, they depend on `user-client` and use Dubbo.
