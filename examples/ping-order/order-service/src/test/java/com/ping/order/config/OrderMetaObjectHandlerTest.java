package com.ping.order.config;

import com.ping.core.context.OperatorType;
import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import com.ping.order.entity.BizOrder;
import com.ping.order.support.TableInfoTestSupport;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单审计字段填充测试：插入写创建信息，更新强制覆盖旧的操作人信息。
 */
class OrderMetaObjectHandlerTest {

    private final OrderMetaObjectHandler handler = new OrderMetaObjectHandler();

    @BeforeAll
    static void initTableInfo() {
        TableInfoTestSupport.init(BizOrder.class);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.set(null);
    }

    @Test
    @DisplayName("插入时填充创建时间、更新人信息和版本号")
    void insertFillsAuditFields() {
        loginAsApp(7L, "张三");
        BizOrder order = new BizOrder();

        handler.insertFill(SystemMetaObject.forObject(order));

        assertNotNull(order.getCreateTime());
        assertNotNull(order.getUpdateTime());
        assertEquals(2, order.getUpdateByType());
        assertEquals(7L, order.getUpdateById());
        assertEquals("张三", order.getUpdateByName());
        assertEquals(0, order.getVersion());
    }

    @Test
    @DisplayName("插入时已有值不被覆盖")
    void insertKeepsProvidedValues() {
        loginAsApp(7L, "张三");
        BizOrder order = new BizOrder();
        LocalDateTime created = LocalDateTime.of(2020, 1, 1, 8, 0);
        order.setCreateTime(created);
        order.setUpdateByName("调用方指定");
        order.setVersion(5);

        handler.insertFill(SystemMetaObject.forObject(order));

        assertEquals(created, order.getCreateTime());
        assertEquals("调用方指定", order.getUpdateByName());
        assertEquals(5, order.getVersion());
    }

    @Test
    @DisplayName("未登录时按匿名操作人填充")
    void insertFillsAnonymousOperator() {
        BizOrder order = new BizOrder();

        handler.insertFill(SystemMetaObject.forObject(order));

        assertEquals(OperatorType.ANONYMOUS.getCode(), order.getUpdateByType());
        assertEquals(0L, order.getUpdateById());
        assertEquals("anonymous", order.getUpdateByName());
    }

    @Test
    @DisplayName("token 中没有名称快照时退化为 user#id")
    void insertFallsBackToUserId() {
        UserContextHolder.set(new UserContext(7L, Set.of("app"), Set.of(), OperatorType.APP));
        BizOrder order = new BizOrder();

        handler.insertFill(SystemMetaObject.forObject(order));

        assertEquals("user#7", order.getUpdateByName());
    }

    @Test
    @DisplayName("更新时强制覆盖实体上的旧操作人信息")
    void updateOverridesAuditFields() {
        loginAsApp(7L, "张三");
        BizOrder order = new BizOrder();
        LocalDateTime oldTime = LocalDateTime.of(2020, 1, 1, 8, 0);
        order.setUpdateByType(OperatorType.PC.getCode());
        order.setUpdateById(99L);
        order.setUpdateByName("旧操作人");
        order.setUpdateTime(oldTime);

        handler.updateFill(SystemMetaObject.forObject(order));

        assertEquals(OperatorType.APP.getCode(), order.getUpdateByType());
        assertEquals(7L, order.getUpdateById());
        assertEquals("张三", order.getUpdateByName());
        assertTrue(order.getUpdateTime().isAfter(oldTime));
    }

    @Test
    @DisplayName("后台用户更新时写入 PC 类型")
    void updateWritesPcOperatorType() {
        UserContextHolder.set(new UserContext(1L, Set.of("admin"), Set.of(),
                OperatorType.PC, "管理员"));
        BizOrder order = new BizOrder();

        handler.updateFill(SystemMetaObject.forObject(order));

        assertEquals(OperatorType.PC.getCode(), order.getUpdateByType());
        assertEquals(1L, order.getUpdateById());
        assertEquals("管理员", order.getUpdateByName());
    }

    private static void loginAsApp(Long id, String name) {
        UserContextHolder.set(new UserContext(id, Set.of("app"), Set.of(),
                OperatorType.APP, name));
    }
}
