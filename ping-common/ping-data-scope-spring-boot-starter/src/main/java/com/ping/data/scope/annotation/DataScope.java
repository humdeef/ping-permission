package com.ping.data.scope.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：声明当前 Mapper 方法需要按登录用户的数据范围过滤。
 *
 * <p>可以直接标在 Mapper 接口上（对该 Mapper 的所有查询生效），
 * 也可以标在具体方法上覆盖接口配置。方法或接口上的
 * {@link IgnoreDataScope} 优先级更高。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 *
 * @author humdeef
 * @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
 * public interface SysUserMapper extends BaseMapper<SysUser> {
 * }
 *
 * @DataScope(table = "ping_project", deptColumn = "dept_id",
 *            ownerColumn = "create_by_id",
 *            ownerTypeColumn = "create_by_type", ownerTypeValue = 1)
 * public interface BizProjectMapper extends BaseMapper<BizProject> {
 * }
 * }</pre>
 *
 * <p>纯 App 域的表（如个人订单）按用户 ID 过滤即可，不要为了套用本注解
 * 硬加 {@code dept_id}；只有归属人、没有部门列时可令
 * {@code deptColumn = ""}，仅按归属人过滤。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 需要过滤的表名。必须显式填写，避免 join/子查询时误伤其他表。
     */
    String table();

    /**
     * SQL 中的表别名；复杂 join 建议填写，留空时自动使用 SQL 解析到的别名。
     */
    String alias() default "";

    /**
     * 部门列。留空表示该表没有部门字段，只按数据归属人过滤。
     */
    String deptColumn() default "dept_id";

    /**
     * 数据归属人列：sys_user 用 id，业务表通常用 create_by_id 或 user_id。
     */
    String ownerColumn() default "create_by_id";

    /**
     * 归属人类型列。用于区分后台用户和 App 用户，避免两套用户 ID 数值相同时串号。
     * 留空表示不限制类型。 <link>OperatorType</link>
     */
    String ownerTypeColumn() default "";

    /**
     * 归属人类型值，配合 {@link #ownerTypeColumn()} 使用；小于 0 表示不限制。
     */
    int ownerTypeValue() default -1;

    /**
     * 是否始终允许“本人数据”。默认开启：dept_id IN (...) OR owner = 当前用户。
     */
    boolean includeSelf() default true;
}
