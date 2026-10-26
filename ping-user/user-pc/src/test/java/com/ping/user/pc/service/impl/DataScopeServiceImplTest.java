package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;
import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysRoleDept;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleDeptMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.pc.service.DataScopeService.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据权限解析测试：5 种数据范围、多角色并集、越权判定。
 */
class DataScopeServiceImplTest {

    private static final Long USER_ID = 100L;
    private static final Long DEPT_ID = 7L;

    private SysUserMapper userMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRoleMapper roleMapper;
    private SysRoleDeptMapper roleDeptMapper;
    private SysDeptMapper deptMapper;
    private DataScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        deptMapper = mock(SysDeptMapper.class);
        service = new DataScopeServiceImpl(userMapper, userRoleMapper, roleMapper, roleDeptMapper, deptMapper);
    }

    private void givenUser(String deptId) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setDeptId(deptId == null ? null : Long.valueOf(deptId));
        when(userMapper.selectById(USER_ID)).thenReturn(user);
    }

    private void givenRoles(SysRole... roles) {
        Set<Long> ids = new HashSet<>();
        for (SysRole role : roles) {
            ids.add(role.getId());
        }
        when(userRoleMapper.findRoleIdsByUserId(USER_ID)).thenReturn(ids);
        when(roleMapper.selectBatchIds(ids)).thenReturn(List.of(roles));
    }

    private static SysRole role(long id, String code, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setDataScope(dataScope);
        return role;
    }

    private static SysDept dept(long id, String ancestors) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setAncestors(ancestors);
        return dept;
    }

    @Test
    @DisplayName("用户不存在：无数据范围，按仅本人处理")
    void resolveMissingUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        Scope scope = service.resolve(USER_ID);

        assertFalse(scope.all());
        assertTrue(scope.selfOnly());
        assertEquals(Set.of(), scope.deptIds());
        assertEquals(USER_ID, scope.userId());
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    @DisplayName("没有任何角色：仅本人")
    void resolveWithoutRoles() {
        givenUser("7");
        when(userRoleMapper.findRoleIdsByUserId(USER_ID)).thenReturn(Set.of());

        Scope scope = service.resolve(USER_ID);

        assertFalse(scope.all());
        assertTrue(scope.selfOnly());
        assertEquals(Set.of(), scope.deptIds());
    }

    @Test
    @DisplayName("拥有 admin 角色：拥有全部数据范围")
    void resolveAdminRole() {
        givenUser("7");
        givenRoles(role(1L, "admin", UserConstants.SCOPE_SELF));

        assertTrue(service.resolve(USER_ID).all());
    }

    @Test
    @DisplayName("某个角色是「全部」：取并集后拥有全部数据范围")
    void resolveScopeAll() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_ALL), role(3L, "guest", UserConstants.SCOPE_SELF));

        Scope scope = service.resolve(USER_ID);

        assertTrue(scope.all());
        assertFalse(scope.selfOnly());
    }

    @Test
    @DisplayName("本部门：只包含自己所在部门")
    void resolveScopeDept() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_DEPT));

        Scope scope = service.resolve(USER_ID);

        assertFalse(scope.all());
        assertFalse(scope.selfOnly());
        assertEquals(Set.of(DEPT_ID), scope.deptIds());
    }

    @Test
    @DisplayName("本部门及下级：按 ancestors 物化路径展开整棵子树")
    void resolveScopeDeptAndChildren() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_DEPT_AND_CHILD));
        when(deptMapper.findAllOrdered()).thenReturn(List.of(
                dept(1L, "0"),
                dept(5L, "0"),
                dept(7L, "0,1"),
                dept(8L, "0,1,7"),
                dept(9L, "0,1,7,8")));

        Scope scope = service.resolve(USER_ID);

        // 7 自己 + 子孙 8、9；祖先 1 与其他分支 5 不在范围内
        assertEquals(Set.of(7L, 8L, 9L), scope.deptIds());
    }

    @Test
    @DisplayName("自定义部门：来自 sys_role_dept")
    void resolveScopeCustom() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_CUSTOM));
        when(roleDeptMapper.selectList(any())).thenReturn(List.of(deptRelation(11L), deptRelation(12L)));

        assertEquals(Set.of(11L, 12L), service.resolve(USER_ID).deptIds());
    }

    @Test
    @DisplayName("仅本人：不产生部门范围，按 selfOnly 标记")
    void resolveScopeSelf() {
        givenUser("7");
        givenRoles(role(2L, "guest", UserConstants.SCOPE_SELF));

        Scope scope = service.resolve(USER_ID);

        assertEquals(Set.of(), scope.deptIds());
        assertTrue(scope.selfOnly());
    }

    @Test
    @DisplayName("dataScope 为空按仅本人处理")
    void resolveNullDataScope() {
        givenUser("7");
        givenRoles(role(2L, "guest", null));

        assertTrue(service.resolve(USER_ID).selfOnly());
    }

    @Test
    @DisplayName("多角色取并集：本部门 + 自定义部门")
    void resolveMergesMultipleRoles() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_DEPT), role(3L, "auditor", UserConstants.SCOPE_CUSTOM));
        when(roleDeptMapper.selectList(any())).thenReturn(List.of(deptRelation(11L)));

        assertEquals(Set.of(7L, 11L), service.resolve(USER_ID).deptIds());
    }

    @Test
    @DisplayName("本人所在部门为空时不会产生 null 部门")
    void resolveWithoutDept() {
        givenUser(null);
        givenRoles(role(2L, "ops", UserConstants.SCOPE_DEPT));

        Scope scope = service.resolve(USER_ID);

        assertEquals(Set.of(), scope.deptIds());
        assertTrue(scope.selfOnly());
    }

    @Test
    @DisplayName("越权判定：全部范围、本人、同部门放行，其他拒绝")
    void canAccessUser() {
        SysUser target = new SysUser();
        target.setId(200L);
        target.setDeptId(7L);
        SysUser self = new SysUser();
        self.setId(USER_ID);
        self.setDeptId(9L);

        assertTrue(service.canAccessUser(new Scope(true, Set.of(), false, USER_ID), target));
        assertTrue(service.canAccessUser(new Scope(false, Set.of(7L), false, USER_ID), target));
        assertTrue(service.canAccessUser(new Scope(false, Set.of(), true, USER_ID), self));
        assertFalse(service.canAccessUser(new Scope(false, Set.of(8L), false, USER_ID), target));
        assertFalse(service.canAccessUser(new Scope(false, Set.of(7L), false, USER_ID), null));
    }

    @Test
    @DisplayName("部门页可见范围：全部范围返回 null 表示不过滤")
    void visibleDeptIdsAll() {
        givenUser("7");
        givenRoles(role(1L, "admin", UserConstants.SCOPE_ALL));

        assertNull(service.visibleDeptIds(USER_ID));
    }

    @Test
    @DisplayName("部门页可见范围：有部门范围时直接返回，仅本人时退回自己部门")
    void visibleDeptIdsFallback() {
        givenUser("7");
        givenRoles(role(2L, "ops", UserConstants.SCOPE_CUSTOM));
        when(roleDeptMapper.selectList(any())).thenReturn(List.of(deptRelation(11L)));
        assertEquals(Set.of(11L), service.visibleDeptIds(USER_ID));

        givenRoles(role(3L, "guest", UserConstants.SCOPE_SELF));
        assertEquals(Set.of(DEPT_ID), service.visibleDeptIds(USER_ID));
    }

    @Test
    @DisplayName("部门树展开：deptId 为空时返回空集合")
    void deptAndChildrenWithoutDept() {
        assertEquals(Set.of(), service.deptAndChildren(null));
        verifyNoInteractions(deptMapper);
    }

    @Test
    @DisplayName("部门树展开：包含自身与全部下级")
    void deptAndChildren() {
        when(deptMapper.findAllOrdered()).thenReturn(List.of(
                dept(7L, "0,1"), dept(8L, "0,1,7"), dept(9L, "0,1,7,8"), dept(5L, "0")));

        assertEquals(Set.of(7L, 8L, 9L), service.deptAndChildren(7L));
    }

    private static SysRoleDept deptRelation(Long deptId) {
        SysRoleDept relation = new SysRoleDept();
        relation.setDeptId(deptId);
        return relation;
    }
}
