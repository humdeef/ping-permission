# examples：示例业务模块

`examples/` 下的模块只用于演示“业务服务如何接入权限底座”，**不是权限内核的一部分**。二开时可以直接复制改造，也可以整个删除。

| 模块 | 说明 |
| --- | --- |
| [ping-order](ping-order/README.md) | 订单示例：PC 后台管理 + App 用户下单，演示 RBAC、双登录域、接口权限、审计与订单状态机 |

## 复制成新业务

1. 复制 `ping-order` 为 `ping-xxx`，修改其 `pom.xml` 的 `artifactId` / `name` / 描述；
2. 在 `examples/pom.xml` 的 `<modules>` 中增加 `<module>ping-xxx</module>`；
3. 修改包名、类名和表名，按需保留 `ping-web` / `ping-dubbo` / `ping-log` / `ping-id` 等 starter；
4. 在权限管理与接口资源中登记新业务的权限码和路径。

## 删除示例

删掉 `examples/`、根 `pom.xml` 的 `<module>examples</module>`、`docker-compose.yml` 的 `order-service`，以及网关的 `/order/**` 路由即可，不影响权限内核。
