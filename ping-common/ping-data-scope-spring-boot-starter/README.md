# ping-data-scope-spring-boot-starter

把「行级数据权限」做成注解 + MyBatis 拦截器：Mapper 上标一次 `@DataScope`，当前用户的部门/本人范围会自动拼进 SQL，业务代码不再手写 `dept_id in (...)`。

## 1. 它解决什么问题

接口级权限只回答「这个用户能不能调这个接口」，回答不了「他能看到这个接口里的哪些行」。典型场景：

- 部门负责人只能看本部门及下级部门的用户/项目；
- 普通员工只能看自己的数据；
- 管理员看全部；
- 自定义角色只能看指定部门。

如果每个列表、详情、修改、删除都手写范围条件，会出现三种问题：

1. 漏写：新加一个查询忘了加范围，就变成越权；
2. 写法不一致：有的用 `dept_id IN`，有的用 `id = ?`，口径对不上；
3. 分页 count 与列表条件不一致，总数泄露真实数据量。

本 starter 把这些收敛到一处：**规则来自 `sys_role.data_scope`，SQL 改写交给 MyBatis-Plus `DataPermissionInterceptor`**。

## 2. 模块位置与依赖

```
业务服务
  └── ping-data-scope-spring-boot-starter
        ├── ping-core                  （UserContextHolder / UserContext）
        ├── mybatis-plus-jsqlparser    （DataPermissionInterceptor / JSqlParser）
        └── spring-boot-autoconfigure
```

业务模块还需要提供：

1. 一个 `DataScopeProvider` Bean：把「当前用户」解析成 `DataScopeInfo`；
2. 把 `DataScopeHandler` 挂进自己的 `MybatisPlusInterceptor`，且**必须排在分页插件之前**。

`user-service` 是规则的维护方（`DataScopeService`），其他后台业务服务通过 Dubbo `DataScopeDubboClient` 获取规则，避免每个业务服务重复实现角色/部门解析。

> 本 starter 面向**后台组织内的业务表**（按部门/归属人隔离）。纯 App 域的表（如 `ping_order`）按 `user_id` 过滤即可，不要为了使用 `@DataScope` 硬加 `dept_id`；如果只有归属人、没有部门列，可以令 `deptColumn = ""`，只按归属人过滤。

## 3. 注解

```java
@DataScope(
        table = "ping_project",     // 必填：只作用于这张表
        alias = "",                 // 可选：join 时显式指定别名
        deptColumn = "dept_id",     // 部门列；留空表示只按归属人过滤
        ownerColumn = "create_by_id",
        ownerTypeColumn = "create_by_type",
        ownerTypeValue = 1,         // 1 = 后台用户，排除 App 用户 ID 串号
        includeSelf = true          // 部门范围之外，始终允许看自己创建的数据
)
public interface BizProjectMapper extends BaseMapper<BizProject> {
}
```

- 标在 **接口** 上：对该 Mapper 的所有 SELECT / UPDATE / DELETE 生效（INSERT 不处理）；
- 标在 **方法** 上：覆盖接口配置；重声明 MyBatis-Plus 的写方法时，必须保留 `@Param`，否则 `#{et.xxx}` / `#{ew.xxx}` 会绑定失败；
- `@IgnoreDataScope`：接口或方法级退出，优先级最高（例如登录日志、系统任务）。

## 4. 生成的 SQL 条件

根据 `DataScopeInfo` 的四种状态：

| 状态 | 追加条件 |
| --- | --- |
| `all = true` | 不追加（管理员 / SCOPE_ALL） |
| 有部门范围 + 允许本人 | `(alias.dept_id IN (...) OR (alias.owner = 当前用户 [AND alias.ownerType = 值]))` |
| 有部门范围 + `includeSelf = false` | `alias.dept_id IN (...)` |
| 仅本人 / 无部门范围 | `(alias.owner = 当前用户 [AND alias.ownerType = 值])` |

条件使用 JSqlParser 解析后注入，因此**分页 count SQL 也会带上同样的条件**，总数不会泄露范围外数据。

## 5. 失败关闭（fail-closed）

以下情况直接抛 `DataScopeException`，不会退化成全量查询：

- 已登录，但容器里没有 `DataScopeProvider`；
- Provider 返回 `null`（不知道当前用户范围）；
- 范围里缺少 `userId`；
- 条件拼接后无法解析。

匿名请求（`UserContextHolder` 没有用户）保持原 SQL，由业务层的登录校验/权限注解决定是否放行。

## 6. 请求级缓存

每个请求只解析一次范围，结果放在 `TransmittableThreadLocal` 中：

- 线程池、`CompletableFuture`、Dubbo 调用继续使用同一份范围；
- 解析期间（`DataScopeContext.isResolving()`）会跳过拦截，避免「查角色/部门」的 SQL 又被数据权限拦截；
- Servlet 过滤器在 `finally` 中清理，防止线程复用串数据。

## 7. 接入步骤

1. 引入依赖：

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-data-scope-spring-boot-starter</artifactId>
</dependency>
```

2. 提供 `DataScopeProvider`：

```java
@Bean
public DataScopeProvider dataScopeProvider(DataScopeService dataScopeService) {
    return () -> {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return null;
        }
        if (UserContextHolder.isAppUser()) {
            throw new DataScopeException("App 用户不支持后台数据权限过滤");
        }
        DataScopeService.Scope scope = dataScopeService.resolve(userId);
        return new DataScopeInfo(scope.all(), scope.deptIds(), scope.selfOnly(), scope.userId());
    };
}
```

3. 挂到 MyBatis-Plus：

```java
interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataScopeHandler)); // 先数据权限
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));    // 后分页
```

4. 在需要过滤的 Mapper 上加 `@DataScope`。

## 8. 使用约束

- **SELECT、UPDATE、DELETE 都会被处理**。MyBatis-Plus 3.5.x 的 `DataPermissionInterceptor` 在查询阶段调用 `beforeQuery`，在 `StatementHandler.prepare` 阶段对 UPDATE / DELETE 调用 `beforePrepare`，因此 `updateById`、`update(entity, wrapper)`、逻辑删除等写语句同样会追加范围条件，越权写入影响 0 行。INSERT 不处理。低于 3.5 的版本需要自行验证是否具备 `beforePrepare` 处理能力。
- **写路径建议仍先做一次范围查询**。虽然 SQL 层已经拦截，但先 `selectScopedById` 再更新/删除可以让越权表现为「数据不存在」，也能区分并发修改（版本号冲突）。本项目的 `SysUserMapper` 写路径同时使用了这两层。
- **匿名请求不追加条件**。没有登录上下文时保持原 SQL，由业务层的登录校验决定是否放行；不要给「匿名也能写」的接口依赖数据权限做保护。
- **表必须显式声明**。`table` 填错或留空不会过滤，属于配置错误；starter 只对匹配的表名改写 SQL。
- **注解的方法必须能被反射加载**。通过 XML 注册、且 `mappedStatementId` 不是 Mapper 接口方法的语句不会应用规则。
- 后台登录域与 App 登录域的用户 ID 可能数值相同。后台表如果确实会被 App 写入（如共用工单表），务必用 `ownerTypeColumn + ownerTypeValue` 限定后台用户；纯 App 表直接按 `user_id` 过滤，不要接入数据权限。
- 关闭开关：`ping.data-scope.enabled=false`（仅建议测试或排障时使用）。

## 9. 与「手写数据范围」的对比

| 维度 | 手写 `dept_id IN (...)` | 注解 + 拦截器 |
| --- | --- | --- |
| 规则来源 | 每个 Service 各写一遍 | `sys_role.data_scope` 统一解析 |
| 新增查询 | 容易漏写 | 标一次注解即可 |
| 分页 count | 容易与列表条件不一致 | 自动一致 |
| 越权详情/修改/删除 | 需要额外判断 | SQL 层自动拦截（SELECT/UPDATE/DELETE） |
| App/后台 ID 串号 | 依赖人工判断 | `ownerTypeColumn` 显式隔离 |
| 排障 | 看业务代码 | 看 SQL 日志中的追加条件 |
