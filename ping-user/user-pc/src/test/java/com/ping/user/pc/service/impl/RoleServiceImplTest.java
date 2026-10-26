package com.ping.user.pc.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.common.constant.UserConstants;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysRoleDept;
import com.ping.user.pc.entity.SysRolePermission;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysRoleDeptMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysRolePermissionMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.pc.model.RoleRequest;
import com.ping.user.pc.service.PermissionCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 角色管理测试：编码不可变、数据范围归一、关联表重写与会话失效。
 */
class RoleServiceImplTest {

    private SysRoleMapper roleMapper;
    private SysRolePermissionMapper rolePermissionMapper;
    private SysRoleDeptMapper roleDeptMapper;
    private SysUserRoleMapper userRoleMapper;
    private PermissionCacheService permissionCacheService;
    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        permissionCacheService = mock(PermissionCacheService.class);
        service = new RoleServiceImpl(roleMapper, rolePermissionMapper, roleDeptMapper,
                userRoleMapper, permissionCacheService);
    }

    @AfterEach
    void clearAudit() {
        AuditContext.getDetailAndClear();
    }

    private static SysRole role(Long id, String code, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName("角色-" + code);
        role.setDataScope(dataScope);
        return role;
    }

    private static RoleRequest request(Long id, String code) {
        RoleRequest req = new RoleRequest();
        req.setId(id);
        req.setCode(code);
        req.setName("角色-" + code);
        return req;
    }

    @Test
    @DisplayName("分页查询：页码从 1 开始，空条件走 selectPage")
    void listUsesOneBasedPage() {
        when(roleMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        IPage<SysRole> page = service.list(2, 5, null, null);

        assertEquals(3, page.getCurrent(), "page=2 对应 MyBatis-Plus 的 current=3");
        assertEquals(5, page.getSize());
    }

    @Test
    @DisplayName("分页查询：带名称条件时走自定义 search")
    void listWithNameUsesSearch() {
        when(roleMapper.search(any(), eq("运营"), isNull())).thenAnswer(inv -> inv.getArgument(0));

        service.list(0, 10, "运营", null);

        verify(roleMapper).search(any(), eq("运营"), isNull());
        verify(roleMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("限定角色范围：空角色集合直接返回空页，不查询数据库")
    void listScopedWithoutRoles() {
        IPage<SysRole> page = service.list(Set.of(), 0, 10, null, null);

        assertEquals(0, page.getRecords().size());
        assertEquals(1, page.getCurrent());
        verifyNoInteractions(roleMapper);
    }

    @Test
    @DisplayName("角色不存在时抛出异常")
    void getMissingRole() {
        when(roleMapper.selectById(9L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.get(9L));

        assertEquals("角色不存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增角色：编码重复时不落库")
    void createDuplicateCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(request(null, "ops")));

        assertEquals("角色编码已存在", ex.getMessage());
        verify(roleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    @DisplayName("新增角色：默认数据范围为仅本人，并记录审计详情")
    void createDefaultsToSelfScope() {
        when(roleMapper.selectCount(any())).thenReturn(0L);

        SysRole saved = service.save(request(null, "ops"));

        assertEquals(UserConstants.SCOPE_SELF, saved.getDataScope(), "新建角色默认最小权限");
        assertEquals(0, saved.getSort().intValue());
        verify(roleMapper).insert(saved);
        assertEquals("新增角色 code=ops", AuditContext.getDetailAndClear());
    }

    @Test
    @DisplayName("新增角色：数据库唯一键冲突转换为业务异常")
    void createDuplicateKey() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_code")).when(roleMapper).insert(any(SysRole.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(request(null, "ops")));

        assertEquals("角色编码已存在", ex.getMessage());
    }

    @Test
    @DisplayName("修改角色：编码不可变更")
    void updateRejectsCodeChange() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.save(request(2L, "ops2")));

        assertEquals("角色编码不可修改", ex.getMessage());
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    @DisplayName("修改角色：dataScope 为空表示不修改")
    void updateKeepsDataScopeWhenNull() {
        SysRole existing = role(2L, "ops", UserConstants.SCOPE_CUSTOM);
        when(roleMapper.selectById(2L)).thenReturn(existing);
        RoleRequest req = request(2L, "ops");
        req.setName("运营");

        service.save(req);

        assertEquals(UserConstants.SCOPE_CUSTOM, existing.getDataScope());
        assertEquals("运营", existing.getName());
    }

    @Test
    @DisplayName("修改角色：dataScope 越界归一为全部")
    void updateNormalizesInvalidDataScope() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));
        RoleRequest req = request(2L, "ops");
        req.setDataScope(99);

        SysRole saved = service.save(req);

        assertEquals(UserConstants.SCOPE_ALL, saved.getDataScope());
    }

    @Test
    @DisplayName("修改角色：permissionIds 为 null 时不动关联表")
    void updateKeepsPermissionsWhenNull() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));

        service.save(request(2L, "ops"));

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
        verify(rolePermissionMapper, never()).insert(any(SysRolePermission.class));
    }

    @Test
    @DisplayName("修改角色：permissionIds 非空时先删后插")
    void updateRewritesPermissions() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));
        RoleRequest req = request(2L, "ops");
        req.setPermissionIds(List.of(7L, 8L));

        service.save(req);

        verify(rolePermissionMapper).deleteByRoleId(2L);
        verify(rolePermissionMapper, org.mockito.Mockito.times(2)).insert(any(SysRolePermission.class));
    }

    @Test
    @DisplayName("修改角色：deptIds 非空时先删后插")
    void updateRewritesDepts() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));
        RoleRequest req = request(2L, "ops");
        req.setDeptIds(List.of(11L));

        service.save(req);

        verify(roleDeptMapper).delete(any());
        verify(roleDeptMapper).insert(any(SysRoleDept.class));
    }

    @Test
    @DisplayName("保存角色后踢出该角色下所有用户")
    void saveInvalidatesRoleUsers() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));
        SysUserRole first = new SysUserRole();
        first.setUserId(7L);
        SysUserRole second = new SysUserRole();
        second.setUserId(8L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(first, second));

        service.save(request(2L, "ops"));

        verify(permissionCacheService).invalidateCache(List.of(7L, 8L));
    }

    @Test
    @DisplayName("删除角色：内置管理员角色受保护")
    void deleteAdminRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "admin", UserConstants.SCOPE_ALL));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(1L));

        assertEquals("内置管理员角色不可删除", ex.getMessage());
        verify(roleMapper, never()).deleteById(any(Long.class));
        verify(permissionCacheService, never()).invalidateCache(any());
    }

    @Test
    @DisplayName("删除角色：清理权限/部门/用户关联并踢出会话")
    void deleteRemovesRelations() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ops", UserConstants.SCOPE_SELF));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(7L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));

        service.delete(2L);

        verify(permissionCacheService).invalidateCache(List.of(7L));
        verify(rolePermissionMapper).deleteByRoleId(2L);
        verify(roleDeptMapper).delete(any());
        verify(userRoleMapper).delete(any());
        verify(roleMapper).deleteById(2L);
        assertEquals("删除角色 ops", AuditContext.getDetailAndClear());
    }

    @Test
    @DisplayName("按 ID 批量查询：空集合不查库")
    void listByIdsEmpty() {
        assertTrue(service.listByIds(Set.of()).isEmpty());
        verifyNoInteractions(roleMapper);
    }

    @Test
    @DisplayName("批量查询角色映射为 ID -> 权限/部门集合")
    void permissionAndDeptIds() {
        SysRolePermission rp = new SysRolePermission();
        rp.setPermissionId(11L);
        SysRolePermission rp2 = new SysRolePermission();
        rp2.setPermissionId(12L);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rp, rp2));
        SysRoleDept rd = new SysRoleDept();
        rd.setDeptId(21L);
        when(roleDeptMapper.selectList(any())).thenReturn(List.of(rd));

        assertEquals(Set.of(11L, 12L), service.getPermissionIds(2L));
        assertEquals(Set.of(21L), service.getDeptIds(2L));
    }

    @Test
    @DisplayName("角色列表按 sort、id 排序")
    void listAllOrdered() {
        List<SysRole> roles = List.of(role(1L, "admin", 1), role(2L, "ops", 4));
        when(roleMapper.selectList(any())).thenReturn(roles);

        assertEquals(roles, service.listAll());
    }

    @Test
    @DisplayName("新增角色时写入权限关联：角色 ID 来自插入结果")
    void createWritesPermissionRelations() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            ((SysRole) inv.getArgument(0)).setId(20L);
            return 1;
        }).when(roleMapper).insert(any(SysRole.class));
        RoleRequest req = request(null, "ops");
        req.setPermissionIds(List.of(7L));

        service.save(req);

        verify(rolePermissionMapper).deleteByRoleId(20L);
        verify(rolePermissionMapper).insert(org.mockito.ArgumentMatchers
                .<SysRolePermission>argThat(
                        rp -> rp.getRoleId().equals(20L) && rp.getPermissionId().equals(7L)));
    }
}
