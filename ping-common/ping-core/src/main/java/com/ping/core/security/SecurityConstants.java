package com.ping.core.security;

/**
 * SecurityConstants - SecurityConstants。
 *
 * @author humdeef
 */
public final class SecurityConstants {

    /** 超级管理员角色编码，与 sys_role.code 保持一致 */
    public static final String ADMIN_ROLE = "admin";

    /** 超级管理员权限通配符 */
    public static final String SUPER_PERMISSION = "*:*:*";

    /**
     * token 会话中保存当前用户展示名的 key。
     *
     * <p>后台与 App 使用各自的 token 会话，因此可以复用同一个 key。
     * 审计字段用它写入名称快照，避免每次写库都跨服务查询用户表。</p>
     */
    public static final String SESSION_NICKNAME = "NICKNAME";

    /**
     * token 会话中保存当前后台用户部门 ID 的 key。
     *
     * <p>数据权限和业务表 dept_id 快照需要用到；App 用户没有该值。</p>
     */
    public static final String SESSION_DEPT_ID = "DEPT_ID";

    private SecurityConstants() {
    }
}
