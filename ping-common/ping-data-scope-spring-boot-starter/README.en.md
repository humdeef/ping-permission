# ping-data-scope-spring-boot-starter

Turns "row-level data permissions" into an annotation + MyBatis interceptor: mark `@DataScope` once on a Mapper, and the current user's department/self scope is automatically appended to the SQL, so business code no longer hand-writes `dept_id in (...)`.

## 1. What problem does it solve

Endpoint-level authorization only answers "can this user call this endpoint"; it cannot answer "which rows in this endpoint can they see". Typical scenarios:

- Department managers can only see users/projects of their own department and sub-departments;
- Regular employees can only see their own data;
- Administrators see everything;
- Custom roles can only see specified departments.

If every list, detail, update, and delete hand-writes scope conditions, three problems arise:

1. Omissions: a newly added query forgets to add the scope and becomes unauthorized access;
2. Inconsistent style: some use `dept_id IN`, others use `id = ?`, and the definitions do not match;
3. Pagination count and list conditions differ, leaking the real data volume through the total.

This starter consolidates these into one place: **rules come from `sys_role.data_scope`, and SQL rewriting is delegated to the MyBatis-Plus `DataPermissionInterceptor`**.

## 2. Module location and dependencies

```
Business service
  └── ping-data-scope-spring-boot-starter
        ├── ping-core                  (UserContextHolder / UserContext)
        ├── mybatis-plus-jsqlparser    (DataPermissionInterceptor / JSqlParser)
        └── spring-boot-autoconfigure
```

The business module also needs to provide:

1. A `DataScopeProvider` bean: resolves the "current user" into `DataScopeInfo`;
2. Mount `DataScopeHandler` into its own `MybatisPlusInterceptor`, and it **must be placed before the pagination plugin**.

`user-service` is the maintainer of the rules (`DataScopeService`); other backend business services obtain rules through the Dubbo `DataScopeDubboClient`, avoiding repeated role/department resolution in every business service.

> This starter targets **business tables within the backend organization** (isolated by department/owner). Pure App-domain tables (e.g. `ping_order`) can simply filter by `user_id`; do not force-add `dept_id` just to use `@DataScope`. If there is only an owner and no department column, you can set `deptColumn = ""` and filter only by owner.

## 3. Annotation

```java
@DataScope(
        table = "ping_project",     // Required: applies only to this table
        alias = "",                 // Optional: explicitly specify the alias for joins
        deptColumn = "dept_id",     // Department column; leave empty to filter only by owner
        ownerColumn = "create_by_id",
        ownerTypeColumn = "create_by_type",
        ownerTypeValue = 1,         // 1 = backend user, prevents App user ID collisions
        includeSelf = true          // Beyond the department scope, always allow viewing data created by oneself
)
public interface BizProjectMapper extends BaseMapper<BizProject> {
}
```

- Applied to an **interface**: takes effect for all SELECT / UPDATE / DELETE of that Mapper (INSERT is not processed);
- Applied to a **method**: overrides the interface configuration; when redeclaring MyBatis-Plus write methods, you must keep `@Param`, otherwise `#{et.xxx}` / `#{ew.xxx}` will fail to bind;
- `@IgnoreDataScope`: opt out at interface or method level, with the highest priority (e.g. login logs, system tasks).

## 4. Generated SQL conditions

According to the four states of `DataScopeInfo`:

| State | Appended condition |
| --- | --- |
| `all = true` | None (administrator / SCOPE_ALL) |
| Has department scope + self allowed | `(alias.dept_id IN (...) OR (alias.owner = current user [AND alias.ownerType = value]))` |
| Has department scope + `includeSelf = false` | `alias.dept_id IN (...)` |
| Self only / no department scope | `(alias.owner = current user [AND alias.ownerType = value])` |

Conditions are injected after JSqlParser parsing, so **the pagination count SQL also carries the same conditions**, and the total does not leak data outside the scope.

## 5. Fail-closed

In the following cases, `DataScopeException` is thrown directly and does not degrade into a full query:

- Logged in, but no `DataScopeProvider` in the container;
- The provider returns `null` (the current user's scope is unknown);
- The scope is missing `userId`;
- The condition cannot be parsed after assembly.

Anonymous requests (no user in `UserContextHolder`) keep the original SQL; whether to allow them is decided by the business layer's login validation/permission annotations.

## 6. Request-level cache

The scope is resolved only once per request, and the result is stored in a `TransmittableThreadLocal`:

- Thread pools, `CompletableFuture`, and Dubbo calls continue to use the same scope;
- During resolution (`DataScopeContext.isResolving()`), interception is skipped, preventing SQL that "queries roles/departments" from being intercepted by data permissions again;
- The servlet filter cleans up in `finally` to prevent data leakage through thread reuse.

## 7. Integration steps

1. Add the dependency:

```xml
<dependency>
    <groupId>com.ping</groupId>
    <artifactId>ping-data-scope-spring-boot-starter</artifactId>
</dependency>
```

2. Provide `DataScopeProvider`:

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

3. Mount to MyBatis-Plus:

```java
interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataScopeHandler)); // Data permissions first
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));    // Pagination after
```

4. Add `@DataScope` to the Mappers that need filtering.

## 8. Usage constraints

- **SELECT, UPDATE, and DELETE are all processed**. MyBatis-Plus 3.5.x's `DataPermissionInterceptor` invokes `beforeQuery` during the query phase and `beforePrepare` for UPDATE / DELETE during `StatementHandler.prepare`, so write statements such as `updateById`, `update(entity, wrapper)`, and logical delete also get the scope condition appended, and unauthorized writes affect 0 rows. INSERT is not processed. Versions below 3.5 need to be verified for `beforePrepare` handling capability.
- **Write paths should still perform a scoped query first**. Although the SQL layer already intercepts, doing `selectScopedById` before updating/deleting makes unauthorized access appear as "data does not exist" and can distinguish concurrent modifications (version conflicts). This project's `SysUserMapper` write path uses both layers.
- **Anonymous requests get no conditions appended**. Without a login context, the original SQL is kept, and the business layer's login validation decides whether to allow the request; do not rely on data permissions to protect endpoints where "anonymous users can also write".
- **Tables must be explicitly declared**. If `table` is wrong or empty, no filtering occurs; this is a configuration error. The starter only rewrites SQL for matching table names.
- **Annotated methods must be loadable via reflection**. Statements registered through XML whose `mappedStatementId` is not a Mapper interface method will not have the rules applied.
- The user IDs of the backend login domain and the App login domain may be numerically the same. If a backend table can actually be written by the App (e.g. a shared work order table), be sure to restrict to backend users with `ownerTypeColumn + ownerTypeValue`; pure App tables should be filtered directly by `user_id` and not integrated with data permissions.
- Off switch: `ping.data-scope.enabled=false` (recommended only for testing or troubleshooting).

## 9. Comparison with "hand-written data scope"

| Dimension | Hand-written `dept_id IN (...)` | Annotation + interceptor |
| --- | --- | --- |
| Rule source | Written once in every Service | Uniformly resolved from `sys_role.data_scope` |
| New queries | Easy to miss | Annotate once |
| Pagination count | Easy to differ from list conditions | Automatically consistent |
| Unauthorized detail/update/delete | Requires extra checks | Automatically intercepted at the SQL layer (SELECT/UPDATE/DELETE) |
| App/backend ID collisions | Relies on manual judgment | Explicitly isolated by `ownerTypeColumn` |
| Troubleshooting | Read business code | Read the appended conditions in the SQL log |
