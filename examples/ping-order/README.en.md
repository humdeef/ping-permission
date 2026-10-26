# examples/ping-order: Order sample business module

> This module lives under `examples/` and is only used to demonstrate how a business service integrates with the permission foundation. It is not part of the permission core. During customization, copy or delete it directly.

`examples/ping-order` is a **sample business module that can be directly replaced/deleted**, used to demonstrate:

- How an ordinary business service integrates with the permission foundation;
- How to call the user service via `user-client`;
- How PC endpoints and App endpoints declare access policies separately;
- How async threads/thread pools propagate user context and `bizTid`.

It is not part of the permission core. During customization you can:

- Keep it as a template and adapt it to your own business;
- Or delete `examples/`, `<module>examples</module>` in the root `pom.xml`, `order-service` in `docker-compose.yml`, and the gateway's `/order/**` route.

## 1. Module structure

```
examples/
├── pom.xml                  # examples aggregate module
└── ping-order/
    ├── pom.xml              # Order aggregate module, groupId com.ping.order
    ├── order-client/        # External Dubbo contract (OrderDubboClient, OrderDTO)
    └── order-service/       # Startup business service
```

## 2. Dependencies

```
order-service
 ├─ order-client
 ├─ user-client                 # Depends only on the user domain contract, not user-pc / user-app
 ├─ ping-web-spring-boot-starter
 ├─ ping-dubbo-spring-boot-starter
 ├─ ping-log-spring-boot-starter
 ├─ ping-id-spring-boot-starter
 ├─ Nacos discovery/config
 ├─ Dubbo + Nacos registry
 └─ Spring Security / Sa-Token / Redis
```

`order-client` does not depend on any business implementation; it is referenced by other services.

## 3. What it demonstrates

| Capability | Location |
| --- | --- |
| PC endpoints + `@PreAuthorize` | `OrderPcController` |
| App endpoints + `@AppLoginOnlyApi` | `OrderAppController` |
| Method-level public override of class-level strategy | `@PublicApi` on `OrderAppController.queryOrderByUserV1` |
| Calling user-service via Dubbo | `OrderLocalServiceImpl` (`UserDubboClient`) |
| Querying App users via Dubbo | `OrderAppController` (`AppUserDubboClient`) |
| Permission data fallback | `DubboPermissionProvider` |
| Async threads + TTL + MDC | `AsyncExecutorConfig`, `OrderPcController.getOrderAsync` |
| Exposing a Dubbo service externally | `OrderDubboServiceImpl` |
| Trace ID | `%X{bizTid}` in `logback.xml` |
| Order ownership and last-modifier auditing | `BizOrder`, `OrderMetaObjectHandler` |
| Order state machine and operation logs | `OrderServiceImpl`, `ping_order_operation_log` |

### 3.1 Order model: App users place orders, the backend only manages them

`examples/ping-order` expresses only one business fact: **App users place orders, and the backend views orders and transitions their statuses**.

| Field | Meaning |
| --- | --- |
| `user_id` / `user_name` | App user who placed the order (order owner, `app_user.id` and its name snapshot) |
| `update_by_type` / `update_by_id` / `update_by_name` | Last modifier (may be an App user or a backend administrator) |

- Order creation is only allowed in the App login domain; `OrderServiceImpl.create` rejects `OperatorType.PC`;
- The main table no longer stores a separate "creator": the creator is `user_id`, and the PC side is not allowed to create. Storing two sets of fields would only make customizers think the backend can also place orders;
- `update_by_*` records the **last operator**, with type codes from `OperatorType`:

```text
1 = PC (sys_user)
2 = APP (app_user)
3 = SYSTEM (scheduled task/message consumption)
4 = ANONYMOUS (not logged in)
```

Name is a **snapshot**, not a foreign key. After a user renames, historical orders still show the name at that time; for a complete trail, query `ping_order_operation_log`, where each log records `operator_type` / `operator_id` / `operator_name`.

`OrderMetaObjectHandler` automatically fills `update_by_*` and `create_time` / `update_time`; the business layer does not need to write these fields manually.

### 3.2 Data visibility: App by `user_id`, backend by endpoint permissions

Orders are not department assets, so the order table does not introduce `dept_id` and does not use `@DataScope` row-level data permissions:

| Caller | Visible scope | Implementation |
| --- | --- | --- |
| App user | Only their own orders | `OrderServiceImpl.page` appends `user_id = current App user`; detail/update/pay/cancel first verify ownership |
| Backend administrator | All orders | `system:order:*` endpoint permissions; roles with that permission can see them |

Unauthorized App-side access manifests as `403 只能访问自己的订单` ("you can only access your own orders"); unauthorized backend roles (such as tom / jerry / lisa) get `403 无此权限：system:order:list` ("missing permission: system:order:list") at the gateway layer, and menus are not delivered either.

**When is `@DataScope` actually needed?** When a business table belongs to the backend organization (for example, `sys_user` isolated by department), and data scopes of "this department / this department and subordinates / self only" are needed. See `SysUserMapper` for an example (`ping-data-scope-spring-boot-starter`). Pure App-domain tables can simply filter by `user_id`; do not force a `dept_id` just to apply `@DataScope` — that would make later customizers think every App table must carry a department field.

## 4. Running

```bash
# First run mvn clean install -DskipTests in the root directory
mvn -pl examples/ping-order/order-service spring-boot:run

# The config namespace follows APP_ENV (dev) by default; for public:
# NACOS_CONFIG_NAMESPACE="" mvn -pl examples/ping-order/order-service spring-boot:run
```

- Port: 8100
- Management port: 8102
- Dependencies: Nacos, Redis, user-service (Dubbo provider)

In the Docker orchestration, order-service starts after MySQL/Redis/Nacos are healthy, and the gateway waits for order-service to be healthy before starting.

## 5. Endpoints and permissions

| Endpoint | Gateway strategy | In-service validation |
| --- | --- | --- |
| `GET /order/pc/queryOrderByUser` | `@LoginOnlyApi` (login only) | Class-level `@PreAuthorize("hasAuthority('system:order:list')")` |
| `GET /order/pc/{userId}` | `sys_api_resource`: GET `/order/**` → `system:order:list` | Class-level `@PreAuthorize` |
| `GET /order/app/queryOrderByUser` | `@AppLoginOnlyApi` (App login) | None |
| `GET /order/app/queryOrderByUser/v1` | Method-level `@PublicApi` (public) | None |

A few points that are easy to misunderstand:

### 5.1 `@LoginOnlyApi` does not bypass the service's `@PreAuthorize`

`OrderPcController` has at class level:

```java
@PreAuthorize("hasAuthority('system:order:list')")
```

The `/order/pc/queryOrderByUser` method also has `@LoginOnlyApi`. When the gateway sees `@LoginOnlyApi`, it skips permission code validation, but after the request enters the service, **the class-level `@PreAuthorize` still applies**. So this endpoint still actually requires `system:order:list`.

If you want "accessible upon login", move the method to a Controller without class-level `@PreAuthorize`, or use an explicit `@authz.has` check.

### 5.2 App endpoints do not check backend permissions

`/order/app/**` is annotated with `@AppLoginOnlyApi`; the gateway only validates App login and does not check `system:order:list`. App users do not have the backend permission system; this is by design.

### 5.3 Order read/write endpoints

Backend management endpoints (`/order/pc/orders`, `OrderPcOrderController`):

| Method and path | Permission code |
| --- | --- |
| `GET /order/pc/orders` | `system:order:list` |
| `GET /order/pc/orders/{id}` | `system:order:list` |
| `GET /order/pc/orders/{id}/logs` | `system:order:list` |
| `PUT /order/pc/orders/{id}` | `system:order:edit` |
| `POST /order/pc/orders/{id}/pay` | `system:order:edit` |
| `POST /order/pc/orders/{id}/cancel` | `system:order:edit` |
| `POST /order/pc/orders/{id}/complete` | `system:order:edit` |
| `DELETE /order/pc/orders/{id}` | `system:order:delete` |

App endpoints (`/order/app/orders`, `OrderAppController`):

| Method and path | Description |
| --- | --- |
| `GET /order/app/orders` | Returns only the current App user's orders |
| `GET /order/app/orders/{id}` | Returns 403 for orders not owned by the caller |
| `GET /order/app/orders/{id}/logs` | Returns 403 for orders not owned by the caller |
| `POST /order/app/orders` | Creates an order; the owner is automatically the current App user |
| `PUT /order/app/orders/{id}` | Can only be modified while pending payment |
| `POST /order/app/orders/{id}/pay` | Pending payment → paid |
| `POST /order/app/orders/{id}/cancel` | Pending payment → cancelled |

The state machine only allows: `pending payment → paid/cancelled`, `paid → completed`. App users cannot call `complete` or delete orders. The backend does not provide a "create order" endpoint: orders can only be created by App users.

`sys_api_resource` already has exact rules configured for the above PC paths (longer than `POST /order/**`, and the gateway prioritizes the longest match), so `pay`/`cancel`/`complete` hit `system:order:edit` and are not mistaken for other permission codes.

## 6. Customization recommendations

- When creating a new business service, copy the structure of `examples/ping-order`: `xxx-client` + `xxx-service`;
- `xxx-service` includes the four starters `ping-web` / `ping-dubbo` / `ping-log` / `ping-id`;
- **Backend business tables** that need row-level data permissions additionally include `ping-data-scope` and provide their own `DataScopeProvider` (cross-service use should reuse `DataScopeDubboClient`); pure App-domain tables can simply filter by `user_id`; do not force a `dept_id` just to apply `@DataScope`;
- When user information is needed, depend on `user-client`, not `user-pc`;
- Each endpoint must choose at least one strategy: `@PublicApi`, `@LoginOnlyApi`, `@AppLoginOnlyApi`, `sys_api_resource`;
- Keep `@PreAuthorize("@authz.has('...')")` for sensitive operations inside services;
- Async thread pools use `TtlRunnable` + MDC `TaskDecorator`.

## 7. Submodule documentation

- [order-client](order-client/README.en.md)
- [order-service](order-service/README.en.md)
