# ping-gateway：API 网关

`ping-gateway` 是后端的统一入口，只有一个可启动模块 [`gateway-service`](gateway-service/README.md)。

- 基于 Spring Cloud Gateway（WebFlux）；
- 负责路由、认证、接口级鉴权、跨域、链路 ID；
- 不做业务逻辑，不连接用户库；
- 通过 `user-client` 的 Dubbo 契约从 user-service 加载鉴权规则。

## 1. 模块结构

```
ping-gateway/
├── pom.xml              # 聚合模块，groupId com.ping.gateway
└── gateway-service/     # 可启动网关
```

## 2. 路由

路由配置在 Nacos 的 `gateway-service-${APP_ENV}.yml`，不在仓库 yml 里：

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

| 路径 | 目标 | 说明 |
| --- | --- | --- |
| `/user/pc/**` | `lb://user-service` | 后台用户接口 |
| `/user/app/**` | `lb://user-service` | C 端用户接口 |
| `/order/**` | `lb://order-service` | 订单示例服务 |

## 3. 职责边界

| 网关负责 | 网关不负责 |
| --- | --- |
| 路由转发 | 业务逻辑 |
| 登录态校验（`StpUtil` / `StpAppUtil`） | 用户数据查询（通过 Dubbo 加载规则除外） |
| 接口级权限码校验 | 数据权限（行级过滤） |
| 白名单/注解策略 | 菜单/按钮权限计算 |
| 路径规范化、跨域 | 审计日志落库 |
| 链路 ID 注入 | 分布式追踪 span |

服务内部仍保留 `@PreAuthorize` 作为第二道防线，防止绕过网关直连。

## 4. 为什么网关不直接查库

网关是 WebFlux，鉴权发生在 Netty 事件循环上。如果在每个请求里同步查 MySQL 或调用 Dubbo：

- 阻塞事件循环，吞吐急剧下降；
- user-service 抖动会直接拖垮网关；
- 数据库连接数被网关占满。

当前设计：

- 鉴权规则：启动时 + 定时通过 Dubbo 拉取，缓存在内存；
- 用户权限：登录时写入 token session（共享 Redis），网关只读 Redis；
- 白名单：定时拉取，缓存在内存。

详见 [`gateway-service`](gateway-service/README.md)。
