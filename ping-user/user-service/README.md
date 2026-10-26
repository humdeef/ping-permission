# user-service：用户服务启动模块

`user-service` 是 `ping-user` 唯一的可启动模块。它**不写业务逻辑**，只负责把 `user-common`、`user-auth`、`user-pc`、`user-app`、`user-client` 和各 starter 装配成一个 Spring Boot 应用。

## 1. 包含什么

```
user-service/
├── pom.xml
└── src/main/
    ├── java/com/ping/user/
    │   ├── UserServiceApplication.java   # main + @MapperScan
    │   └── config/
    │       ├── AsyncExecutorConfig.java  # tt-AsyncExecutor / auditExecutor + MDC
    │       ├── MyBatisPlusConfig.java    # SqlSessionFactory、分页、自动填充
    │       └── SecurityConfig.java       # @EnableAsync/@EnableScheduling + 安全配置绑定
    └── resources/
        ├── application.yml               # 只保留 Nacos 引导配置
        ├── db/migration/                 # Flyway 迁移脚本（V1__init_user_schema.sql）
        └── logback.xml                   # 控制台日志，含 bizTid
```

依赖（除业务模块外）：

- `ping-web-spring-boot-starter`：Sa-Token 桥接、`@PreAuthorize`、统一异常、注解扫描；
- `ping-dubbo-spring-boot-starter`：用户上下文 / bizTid 透传；
- `ping-log-spring-boot-starter`：`X-Biz-Tid`、MDC；
- `ping-id-spring-boot-starter`：雪花 ID；
- Nacos discovery/config、MyBatis-Plus、MySQL、Redis、Sa-Token、Caffeine、Actuator/Prometheus；
- `springdoc-openapi-starter-webmvc-ui`：生成 `/v3/api-docs`，UI 由网关的 `http://localhost:9999/docs` 统一提供。

## 2. 启动类

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

为什么 `@MapperScan` 要写两个包？

- `user-pc` 的 Mapper 在 `com.ping.user.pc.mapper`；
- `user-app` 的 Mapper 在 `com.ping.user.app.mapper`。

`MyBatisPlusConfig` 上也有一份相同的 `@MapperScan`。两处重复不冲突，但新增 Mapper 包时**两处都要改**，否则会出现 “Mapper 未注册”。

## 3. 启动配置

`src/main/resources/application.yml` 只保留引导信息：

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

运行配置在 Nacos：

- `application-${APP_ENV}.yml`：Redis、Sa-Token、Dubbo registry、Actuator、优雅停机；
- `user-service-${APP_ENV}.yml`：端口 8099、数据源、MyBatis-Plus、Dubbo provider、`ping.security`、审计保留天数。

配置缺失会快速失败，不会“半套配置”启动。Docker 首次启动由 `init-sql/nacos-mysql.sql` 导入。

## 4. 关键配置类

### 4.1 `SecurityConfig`

```java
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PingSecurityProperties.class)
public class SecurityConfig {
}
```

- `@EnableAsync`：审计/登录日志异步写入；
- `@EnableScheduling`：`DataRetentionJob` 等定时任务；
- `@EnableConfigurationProperties`：绑定 `ping.security.*`。

注意 `ping-web-spring-boot-starter` 已经提供 `SecurityFilterChain`、`PasswordEncoder`、`SaTokenAuthFilter` 等；这里的 `SecurityConfig` 不重复定义安全链，只做开关和配置绑定。

### 4.2 `MyBatisPlusConfig`

手工构建 `SqlSessionFactory`，原因是 Spring Boot 4 + MyBatis-Plus 的自动配置顺序存在兼容问题：

```java
@Bean
public SqlSessionFactory sqlSessionFactory() throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
    factory.setConfiguration(mybatisConfiguration());
    factory.setPlugins(mybatisPlusInterceptor());
    GlobalConfig globalConfig = new GlobalConfig();
    // 手工构建时 MetaObjectHandler 必须显式挂载，否则 create_time/update_time 为 NULL
    globalConfig.setMetaObjectHandler(metaObjectHandler());
    factory.setGlobalConfig(globalConfig);
    return factory.getObject();
}
```

包含：

- `PaginationInnerInterceptor(DbType.MYSQL)`：分页；
- `MetaObjectHandler`：自动填充 `createTime` / `updateTime`；
- 全局 `id-type: AUTO`。

**坑**：如果你自己又定义了一个 `SqlSessionFactory`，或把 `mapper-locations` 改错，会出现 `Invalid bound statement (not found)` 或自动填充失效。

### 4.3 `AsyncExecutorConfig`

| Bean | 用途 | 线程 |
| --- | --- | --- |
| `tt-AsyncExecutor` | 业务异步 | core 2 / max 8 / queue 100 |
| `auditExecutor` | 审计、登录日志 | core 1 / max 4 / queue 2000 |

两者都设置了 `TaskDecorator`，把调用线程的 MDC（含 `bizTid`）带到工作线程；拒绝策略是 `CallerRunsPolicy`，队列满时由调用线程执行，避免丢日志。

> 这里的 `TaskDecorator` 只传递 MDC，不传递 `UserContextHolder`。如果需要异步任务里使用用户上下文，用 `TtlRunnable.get(...)` 包装，或参考 order-service 的 `TtlRunnable` 用法。

## 5. 运行

### 5.1 本地

```bash
# 先在根目录构建并安装依赖模块
mvn clean install -DskipTests

# 启动 user-service（不要加 -am，否则会在聚合模块上执行 run 而报“找不到主类”）
mvn -pl ping-user/user-service spring-boot:run

# 配置命名空间默认跟随 APP_ENV（dev）；如需 public：
# NACOS_CONFIG_NAMESPACE="" mvn -pl ping-user/user-service spring-boot:run

# 方式二：直接运行 jar
java -jar ping-user/user-service/target/user-service.jar
```

默认端口 8099，管理端口 8101。启动前需要：

- MySQL：库 `ping`，表结构与种子数据由本服务启动时的 Flyway 迁移创建（`src/main/resources/db/migration/`）；
- Redis；
- Nacos，且存在 `application-dev.yml`、`user-service-dev.yml`：默认读 `dev` 命名空间（跟随 `APP_ENV`）；配置在 public 时设置 `NACOS_CONFIG_NAMESPACE=""`。

如果本地 MySQL/Redis 不是 3306/6379，用 `MYSQL_HOST`、`MYSQL_PORT`、`REDIS_HOST`、`REDIS_PORT` 覆盖；Nacos 地址用 `NACOS_HOST`、`NACOS_PORT`。

> 典型报错：`Failed to configure a DataSource`。先往上找 `config[... ] is empty`，通常是 Nacos 命名空间不对或 dataId 不存在，而不是数据库本身的问题。

### 5.2 Docker

```bash
./start.sh                  # 构建 + 启动全部容器
./start.sh logs user-service
./start.sh status
```

容器内通过服务名访问 MySQL/Redis/Nacos，环境变量由 `docker-compose.yml` 注入。

### 5.3 切换环境

```bash
APP_ENV=test mvn -pl ping-user/user-service spring-boot:run
APP_ENV=test ./start.sh
```

`APP_ENV` 同时决定：

- Nacos 配置 dataId 后缀（`application-test.yml`、`user-service-test.yml`）；
- 服务发现命名空间（`test`）；
- Dubbo 注册命名空间（`dubbo-test`）。

## 6. 健康检查

```bash
curl -s http://localhost:8101/actuator/health
```

Docker Compose 用该端点判断 `user-service` 是否就绪，gateway 会等它 healthy 后再启动，避免网关启动时 `PermissionDubboClient` 还没有提供者、鉴权规则为空。

## 7. 常见坑

### 7.1 `Table 'ping.xxx' doesn't exist`

表结构由 Flyway 迁移维护（`src/main/resources/db/migration/`），V1 覆盖所有运行期表（包括 `sys_login_log`、`sys_operation_log`）。如果确认库中存在旧表但缺少新表，先看迁移日志；历史表中残留 `success=0` 记录时需先修掉：

```bash
mysql -uroot -p ping -e 'DELETE FROM flyway_schema_history_user WHERE success = 0'
```

要让迁移从空库完整重跑：

```bash
./start.sh clean   # 删除数据卷，下次启动由 Flyway 重新建表
```

### 7.2 Nacos 配置缺失导致启动失败

```
Could not resolve placeholder ... / Config data resource not found
```

检查默认命名空间下是否有：

```
application-dev.yml
user-service-dev.yml
```

以及 `spring.config.import` 的后缀是否和 dataId 一致。

### 7.3 服务注册到了错误的命名空间

`spring.cloud.nacos.discovery.namespace` 使用 `${APP_ENV:dev}`。如果启动时没有传 `APP_ENV`，默认是 `dev`。Nacos 控制台看不到服务时，先确认命名空间选的是 `dev`，不是 `public`。

### 7.4 Dubbo 提供者未注册

检查 Nacos 配置：

```yaml
dubbo:
  scan:
    base-packages: com.ping.user.pc.service,com.ping.user.app.service
```

`@DubboService` 的实现类必须在这两个包下。网关报“无提供者”时，先看 user-service 启动日志中的 Dubbo export 信息。

### 7.5 `@MapperScan` 漏包

新增一个 `com.ping.user.xxx.mapper` 包时，需要同时修改：

- `UserServiceApplication` 的 `@MapperScan`；
- `MyBatisPlusConfig` 的 `@MapperScan`。

否则 Mapper 不会被注册，启动或调用时报 `NoSuchBeanDefinitionException`。

### 7.6 异步任务里没有 `bizTid`

`AsyncExecutorConfig` 的 `TaskDecorator` 会复制 MDC，但前提是任务提交时 MDC 里已经有 `bizTid`。如果请求没有经过 `BizTraceIdFilter`（例如定时任务、消息消费），需要自己生成并放入 MDC。

### 7.7 改端口后 Docker 健康检查失败

`docker-compose.yml` 的 healthcheck 和 Nacos 配置里的 `server.port` 是两处独立配置。改端口时两边都要改，否则容器一直 unhealthy。

## 8. 依赖方式

`user-service` 是启动模块，通常不被打包成库供其他模块依赖。其他服务需要用户能力时，依赖 `user-client` 走 Dubbo。
