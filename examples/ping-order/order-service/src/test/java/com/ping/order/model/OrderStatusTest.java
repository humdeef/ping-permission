package com.ping.order.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机测试：只允许 待支付→已支付/已取消、已支付→已完成。
 */
class OrderStatusTest {

    @Test
    @DisplayName("待支付可以支付或取消")
    void pendingCanPayOrCancel() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("已支付只能完成")
    void paidCanOnlyComplete() {
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("已取消和已完成是终态")
    void terminalStatesCannotTransition() {
        for (OrderStatus target : OrderStatus.values()) {
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(target));
            assertFalse(OrderStatus.COMPLETED.canTransitionTo(target));
        }
    }

    @Test
    @DisplayName("目标状态为空时不允许流转")
    void nullTargetIsRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(status.canTransitionTo(null));
        }
    }

    @Test
    @DisplayName("编码解析：0/1/2/3 与描述")
    void parsesCode() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromCode(0));
        assertEquals(OrderStatus.PAID, OrderStatus.fromCode(1));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromCode(2));
        assertEquals(OrderStatus.COMPLETED, OrderStatus.fromCode(3));
        assertEquals("待支付", OrderStatus.PENDING.getDescription());
    }

    @Test
    @DisplayName("未知或空编码抛出异常")
    void rejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.fromCode(9));
    }
}
