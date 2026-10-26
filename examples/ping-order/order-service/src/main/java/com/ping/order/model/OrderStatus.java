package com.ping.order.model;

/**
 * OrderStatus - 订单状态机。
 *
 * <pre>
 * 0 待支付 ──支付──▶ 1 已支付 ──完成──▶ 3 已完成
 *    └──────取消──────▶ 2 已取消
 * </pre>
 *
 * @author humdeef
 */
public enum OrderStatus {

    /** 待支付。 */
    PENDING(0, "待支付"),
    /** 已支付。 */
    PAID(1, "已支付"),
    /** 已取消。 */
    CANCELLED(2, "已取消"),
    /** 已完成。 */
    COMPLETED(3, "已完成");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code != null) {
            for (OrderStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }

    /**
     * 是否允许从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        if (this == PENDING) {
            return target == PAID || target == CANCELLED;
        }
        if (this == PAID) {
            return target == COMPLETED;
        }
        return false;
    }
}
