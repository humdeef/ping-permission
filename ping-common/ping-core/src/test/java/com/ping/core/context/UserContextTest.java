package com.ping.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户上下文与权限匹配的单元测试。
 */
class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.set(null);
    }

    @Test
    @DisplayName("权限精确匹配与 xx:* 通配匹配")
    void hasPermissionSupportsExactAndWildcard() {
        UserContext context = new UserContext(1L, Set.of("admin"),
                Set.of("system:user:list", "system:role:*"), OperatorType.PC);

        assertTrue(context.hasPermission("system:user:list"));
        assertTrue(context.hasPermission("system:role:edit"));
        assertTrue(context.hasPermission("system:role:delete"));
        assertFalse(context.hasPermission("system:user:edit"));
        assertFalse(context.hasPermission(null));
    }

    @Test
    @DisplayName("构造后修改原集合不影响上下文")
    void contextCopiesCollections() {
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        roles.add("admin");
        permissions.add("system:user:list");

        UserContext context = new UserContext(1L, roles, permissions, OperatorType.PC);
        roles.add("guest");
        permissions.add("system:user:delete");

        assertEquals(Set.of("admin"), context.getRoles());
        assertEquals(Set.of("system:user:list"), context.getPermissions());
        assertFalse(context.hasPermission("system:user:delete"));
    }

    @Test
    @DisplayName("没有显式类型时根据 app 角色推断操作人类型")
    void infersOperatorTypeFromRoles() {
        assertEquals(OperatorType.APP,
                new UserContext(2L, Set.of("app"), Set.of()).getOperatorType());
        assertEquals(OperatorType.PC,
                new UserContext(3L, Set.of("admin"), Set.of()).getOperatorType());
        assertEquals(OperatorType.ANONYMOUS,
                new UserContext(null, Set.of(), Set.of()).getOperatorType());
        assertEquals(OperatorType.APP, OperatorType.fromRoles(Set.of("app"), 9L));
        assertEquals(OperatorType.ANONYMOUS, OperatorType.fromRoles(Set.of(), null));
    }

    @Test
    @DisplayName("操作人类型编码解析：未知编码按匿名处理")
    void parsesOperatorTypeCode() {
        assertEquals(OperatorType.PC, OperatorType.fromCode(1));
        assertEquals(OperatorType.APP, OperatorType.fromCode(2));
        assertEquals(OperatorType.SYSTEM, OperatorType.fromCode(3));
        assertEquals(OperatorType.ANONYMOUS, OperatorType.fromCode(4));
        assertEquals(OperatorType.ANONYMOUS, OperatorType.fromCode(null));
        assertEquals(OperatorType.ANONYMOUS, OperatorType.fromCode(99));
    }

    @Test
    @DisplayName("UserContextHolder 区分 PC / App / 未登录")
    void holderDistinguishesLoginDomains() {
        assertFalse(UserContextHolder.isLogin());
        assertEquals(OperatorType.ANONYMOUS, UserContextHolder.getOperatorType());

        UserContextHolder.set(new UserContext(7L, Set.of(), Set.of(), OperatorType.APP, "张三"));
        assertTrue(UserContextHolder.isLogin());
        assertTrue(UserContextHolder.isAppUser());
        assertFalse(UserContextHolder.isPcUser());
        assertEquals(7L, UserContextHolder.getUserId());
        assertEquals("张三", UserContextHolder.getUserName());

        UserContextHolder.set(new UserContext(1L, Set.of(), Set.of(), OperatorType.PC, "管理员"));
        assertTrue(UserContextHolder.isPcUser());
        assertFalse(UserContextHolder.isAppUser());

        UserContextHolder.set(null);
        assertFalse(UserContextHolder.isLogin());
    }
}
