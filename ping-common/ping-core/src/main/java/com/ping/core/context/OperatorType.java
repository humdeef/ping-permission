package com.ping.core.context;

import java.util.Set;

/**
 * OperatorType - 操作人类型。
 *
 * <p>同一张业务表可能同时被后台用户（{@code sys_user}）、App 用户（{@code app_user}）
 * 和系统任务修改。仅用 {@code update_id} 无法判断这个 ID 属于哪张用户表，
 * 因此审计字段统一使用「操作人类型 + 操作人 ID + 操作人名称快照」。</p>
 *
 * <p>数据库中使用 {@link #getCode()} 存储，避免枚举名变更影响历史数据。</p>
 *
 * @author humdeef
 */
public enum OperatorType {

    /** 后台用户，对应 {@code sys_user.id}。 */
    PC(1, "后台用户"),

    /** App 用户，对应 {@code app_user.id}。 */
    APP(2, "App用户"),

    /** 系统任务 / 定时任务 / 消息消费。 */
    SYSTEM(3, "系统"),

    /** 未登录或无法识别的调用方。 */
    ANONYMOUS(4, "匿名");

    /** 旧版 App 用户角色标识。 */
    private static final String APP_ROLE = "app";

    /** 写入数据库的稳定编码。 */
    private final int code;

    /** 展示名称。 */
    private final String description;

    OperatorType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPc() {
        return this == PC;
    }

    public boolean isApp() {
        return this == APP;
    }

    /**
     * 按数据库编码还原；未知编码按匿名处理，避免把历史数据误判成后台用户。
     */
    public static OperatorType fromCode(Integer code) {
        if (code == null) {
            return ANONYMOUS;
        }
        for (OperatorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return ANONYMOUS;
    }

    /**
     * 兼容旧调用：没有显式类型时，根据 {@code app} 角色推断。
     */
    public static OperatorType fromRoles(Set<String> roles, Long userId) {
        if (roles != null && roles.contains(APP_ROLE)) {
            return APP;
        }
        return userId == null ? ANONYMOUS : PC;
    }
}
