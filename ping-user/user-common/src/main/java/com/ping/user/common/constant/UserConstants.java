package com.ping.user.common.constant;

/**
 * 用户域公共常量。
 *
 * <p>接口、实现类、Controller 统一引用这里，避免同一常量在多个文件中重复定义。</p>
 *
 * @author humdeef
 */
public final class UserConstants {

    private UserConstants() {
    }

    // ---------------- 数据权限 ----------------

    /** 数据范围：全部。 */
    public static final int SCOPE_ALL = 1;
    /** 数据范围：本部门。 */
    public static final int SCOPE_DEPT = 2;
    /** 数据范围：本部门及下级。 */
    public static final int SCOPE_DEPT_AND_CHILD = 3;
    /** 数据范围：仅本人。 */
    public static final int SCOPE_SELF = 4;
    /** 数据范围：自定义部门。 */
    public static final int SCOPE_CUSTOM = 5;

    // ---------------- 会话 ----------------

    /** token 会话中保存登录 IP 的 key。 */
    public static final String SESSION_IP = "LOGIN_IP";
    /** token 会话中保存 User-Agent 的 key。 */
    public static final String SESSION_USER_AGENT = "LOGIN_UA";
    /** App token 会话中保存手机号的 key。 */
    public static final String APP_PHONE = "APP_PHONE";
    /** 后台 token 会话中保存用户展示名的 key（App 登录复用同一个 key）。 */
    public static final String SESSION_NICKNAME = com.ping.core.security.SecurityConstants.SESSION_NICKNAME;

    // ---------------- 业务规则 ----------------

    /** App 用户状态：禁用。 */
    public static final int APP_USER_STATUS_DISABLED = 0;
    /** App 用户状态：正常。 */
    public static final int APP_USER_STATUS_NORMAL = 1;
    /** App 用户状态：注销中。 */
    public static final int APP_USER_STATUS_CANCELLING = 2;

    /** 内置管理员用户 ID。 */
    public static final long BUILT_IN_ADMIN_ID = 1L;
    /** 手机号身份提供方。 */
    public static final String PROVIDER_PHONE = "phone";
    /** 操作日志导出最大条数。 */
    public static final int EXPORT_LIMIT = 10000;
    /** 审计详情最大长度。 */
    public static final int DETAIL_MAX_LENGTH = 512;

    // ---------------- 认证 Redis key ----------------

    /** 验证码 Redis key 前缀。 */
    public static final String CAPTCHA_KEY_PREFIX = "ping:security:captcha:";
    /** 登录失败计数 Redis key 前缀。 */
    public static final String LOGIN_FAIL_KEY_PREFIX = "ping:security:login:fail:";
    /** 账号锁定 Redis key 前缀。 */
    public static final String LOGIN_LOCK_KEY_PREFIX = "ping:security:login:lock:";
}
