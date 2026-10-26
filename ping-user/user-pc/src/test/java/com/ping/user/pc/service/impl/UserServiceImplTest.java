package com.ping.user.pc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.auth.config.PingSecurityProperties;
import com.ping.user.auth.security.PasswordPolicy;
import com.ping.user.common.constant.UserConstants;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.pc.model.UserRequest;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.PermissionCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户管理测试：内置管理员保护、密码策略、角色变更后的会话失效与数据范围过滤。
 */
class UserServiceImplTest {

    private SysUserMapper userMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRoleMapper roleMapper;
    private SysDeptMapper deptMapper;
    private PasswordEncoder passwordEncoder;
    private PermissionCacheService permissionCacheService;
    private DataScopeService dataScopeService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        deptMapper = mock(SysDeptMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        permissionCacheService = mock(PermissionCacheService.class);
        dataScopeService = mock(DataScopeService.class);
        service = new UserServiceImpl(userMapper, userRoleMapper, roleMapper, deptMapper,
                passwordEncoder, permissionCacheService, dataScopeService,
                new PasswordPolicy(new PingSecurityProperties()));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        doAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setId(10L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
    }

    @AfterEach
    void clearAudit() {
        AuditContext.getDetailAndClear();
    }

    private static SysUser user(long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Nick-" + username);
        user.setStatus(1);
        return user;
    }

    private static UserRequest createRequest(String username, String password) {
        UserRequest req = new UserRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private AtomicReference<QueryWrapper<SysUser>> captureScopedWrapper() {
        AtomicReference<QueryWrapper<SysUser>> ref = new AtomicReference<>();
        when(userMapper.selectScopedPage(any(), any())).thenAnswer(inv -> {
            ref.set(inv.getArgument(1));
            return inv.getArgument(0);
        });
        return ref;
    }

    // ---------------- 分页查询 ----------------

    @Test
    @DisplayName("分页查询：页码从 1 开始")
    void listScopedPageNormalization() {
        captureScopedWrapper();

        IPage<SysUser> page = service.listScoped(-3, 20, null, null, null, false);

        assertEquals(1, page.getCurrent());
        assertEquals(20, page.getSize());
    }

    @Test
    @DisplayName("分页查询：用户名/昵称转为模糊条件")
    void listScopedSearchConditions() {
        AtomicReference<QueryWrapper<SysUser>> ref = captureScopedWrapper();

        service.listScoped(0, 10, "bo", "Bob", null, false);
        ref.get().getSqlSegment();

        Map<String, Object> params = ref.get().getParamNameValuePairs();
        assertTrue(params.containsValue("%bo%"), "用户名应转为 like 条件：" + params);
        assertTrue(params.containsValue("%Bob%"), "昵称应转为 like 条件：" + params);
    }

    @Test
    @DisplayName("分页查询：空白搜索词不产生条件")
    void listScopedIgnoresBlankSearch() {
        AtomicReference<QueryWrapper<SysUser>> ref = captureScopedWrapper();

        service.listScoped(0, 10, "  ", "", null, false);
        ref.get().getSqlSegment();

        assertTrue(ref.get().getParamNameValuePairs().isEmpty());
    }

    @Test
    @DisplayName("部门过滤：不包含下级时只过滤当前部门")
    void listScopedFiltersSingleDept() {
        AtomicReference<QueryWrapper<SysUser>> ref = captureScopedWrapper();

        service.listScoped(0, 10, null, null, 7L, false);
        String sql = ref.get().getSqlSegment();

        assertTrue(sql.contains("dept_id"), sql);
        assertTrue(ref.get().getParamNameValuePairs().containsValue(7L));
    }

    @Test
    @DisplayName("部门过滤：包含下级时展开整棵部门树")
    void listScopedIncludesChildren() {
        when(dataScopeService.deptAndChildren(7L)).thenReturn(Set.of(8L, 9L));
        AtomicReference<QueryWrapper<SysUser>> ref = captureScopedWrapper();

        service.listScoped(0, 10, null, null, 7L, true);
        ref.get().getSqlSegment();

        Map<String, Object> params = ref.get().getParamNameValuePairs();
        assertTrue(params.containsValue(8L));
        assertTrue(params.containsValue(9L));
    }

    @Test
    @DisplayName("部门过滤：部门树为空时使用 1 = 0，避免查出全量")
    void listScopedEmptyDeptTree() {
        when(dataScopeService.deptAndChildren(7L)).thenReturn(Set.of());
        AtomicReference<QueryWrapper<SysUser>> ref = captureScopedWrapper();

        service.listScoped(0, 10, null, null, 7L, true);
        String sql = ref.get().getSqlSegment();

        assertTrue(sql.contains("1 = 0"), "空部门树必须拼出恒假条件：" + sql);
    }

    // ---------------- 新增 ----------------

    @Test
    @DisplayName("新增用户：用户名重复时拒绝")
    void createDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.save(createRequest("tom", "Abc12345")));

        assertEquals("用户名已存在", ex.getMessage());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("新增用户：必须设置初始密码")
    void createWithoutPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.save(createRequest("tom", "  ")));

        assertEquals("新增用户必须设置初始密码", ex.getMessage());
    }

    @Test
    @DisplayName("新增用户：密码不满足复杂度策略")
    void createWithWeakPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.save(createRequest("tom", "123")));

        assertEquals("密码长度不能少于 8 位", ex.getMessage());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("新增用户：密码加密、强制改密、用户名去空格")
    void createUser() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        SysUser saved = service.save(createRequest("  tom  ", "Abc12345"));

        assertEquals("tom", saved.getUsername());
        assertEquals("ENC(Abc12345)", saved.getPassword());
        assertEquals(1, saved.getForceChangePassword().intValue());
        assertEquals(1, saved.getStatus().intValue());
        assertNotNull(saved.getPasswordUpdatedAt());
        verify(userMapper).insert(saved);
        assertEquals("新增用户 id=10, username=tom", AuditContext.getDetailAndClear());
    }

    @Test
    @DisplayName("新增用户：同时分配角色")
    void createUserWithRoles() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        UserRequest req = createRequest("tom", "Abc12345");
        req.setRoleIds(List.of(3L));

        service.save(req);

        verify(userRoleMapper).deleteByUserId(10L);
        verify(userRoleMapper).insert(org.mockito.ArgumentMatchers
                .<SysUserRole>argThat(ur -> ur.getUserId().equals(10L) && ur.getRoleId().equals(3L)));
        verify(permissionCacheService).invalidateCache(List.of(10L));
    }

    @Test
    @DisplayName("新增用户：部门不存在时拒绝")
    void createWithMissingDept() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectById(7L)).thenReturn(null);
        UserRequest req = createRequest("tom", "Abc12345");
        req.setDeptId(7L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("部门不存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增用户：部门存在时写入 deptId")
    void createWithDept() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectById(7L)).thenReturn(new SysDept());
        UserRequest req = createRequest("tom", "Abc12345");
        req.setDeptId(7L);

        assertEquals(7L, service.save(req).getDeptId());
    }

    // ---------------- 修改 ----------------

    @Test
    @DisplayName("修改用户：用户不存在时抛出异常")
    void updateMissingUser() {
        when(userMapper.selectById(5L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            UserRequest req = createRequest("bob", null);
            req.setId(5L);
            service.save(req);
        });

        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    @DisplayName("修改用户：内置管理员不可禁用")
    void updateCannotDisableBuiltInAdmin() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        UserRequest req = createRequest("admin", null);
        req.setId(1L);
        req.setStatus(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("内置管理员不可禁用", ex.getMessage());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("修改用户：无变化时记录审计详情，不踢出会话")
    void updateProfile() {
        SysUser existing = user(5L, "bob");
        when(userMapper.selectById(5L)).thenReturn(existing);
        UserRequest req = createRequest("bob", null);
        req.setId(5L);
        req.setNickname("Bobby");

        service.save(req);

        assertEquals("Bobby", existing.getNickname());
        assertEquals("bob", existing.getUsername());
        verify(userMapper).updateById(existing);
        verify(permissionCacheService, never()).invalidateCache(any());
        assertEquals("[nickname: Nick-bob -> Bobby]", AuditContext.getDetailAndClear());
    }

    @Test
    @DisplayName("修改用户：修改密码后强制改密并踢出会话")
    void updatePassword() {
        SysUser existing = user(5L, "bob");
        when(userMapper.selectById(5L)).thenReturn(existing);
        UserRequest req = createRequest("bob", "NewPass123");
        req.setId(5L);

        service.save(req);

        assertEquals("ENC(NewPass123)", existing.getPassword());
        assertEquals(1, existing.getForceChangePassword().intValue());
        assertNotNull(existing.getPasswordUpdatedAt());
        verify(permissionCacheService).invalidateCache(List.of(5L));
    }

    @Test
    @DisplayName("修改用户：弱密码被策略拒绝，不落库")
    void updateWithWeakPassword() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));
        UserRequest req = createRequest("bob", "abc");
        req.setId(5L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("密码长度不能少于 8 位", ex.getMessage());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("修改用户：角色变化时重写关联并踢出会话")
    void updateRolesInvalidatesSession() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));
        when(userRoleMapper.findRoleIdsByUserId(5L)).thenReturn(Set.of(2L));
        UserRequest req = createRequest("bob", null);
        req.setId(5L);
        req.setRoleIds(List.of(3L));

        service.save(req);

        verify(userRoleMapper).deleteByUserId(5L);
        verify(userRoleMapper).insert(org.mockito.ArgumentMatchers
                .<SysUserRole>argThat(ur -> ur.getRoleId().equals(3L)));
        verify(permissionCacheService).invalidateCache(List.of(5L));
    }

    @Test
    @DisplayName("修改用户：角色未变化时不踢出会话")
    void updateSameRolesKeepsSession() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));
        when(userRoleMapper.findRoleIdsByUserId(5L)).thenReturn(Set.of(3L));
        UserRequest req = createRequest("bob", null);
        req.setId(5L);
        req.setRoleIds(List.of(3L));

        service.save(req);

        verify(permissionCacheService, never()).invalidateCache(any());
    }

    @Test
    @DisplayName("修改用户：内置管理员必须保留 admin 角色")
    void updateAdminMustKeepAdminRole() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        SysRole adminRole = new SysRole();
        adminRole.setId(9L);
        adminRole.setCode("admin");
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        UserRequest req = createRequest("admin", null);
        req.setId(1L);
        req.setRoleIds(List.of(10L));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("内置管理员必须保留超级管理员角色", ex.getMessage());
        verify(userRoleMapper, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("修改用户：内置管理员保留 admin 角色时正常保存")
    void updateAdminWithAdminRole() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        SysRole adminRole = new SysRole();
        adminRole.setId(9L);
        adminRole.setCode("admin");
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        UserRequest req = createRequest("admin", null);
        req.setId(1L);
        req.setRoleIds(List.of(9L));

        service.save(req);

        verify(userRoleMapper).deleteByUserId(1L);
    }

    @Test
    @DisplayName("修改用户：变更到不存在的部门时拒绝")
    void updateWithMissingDept() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));
        when(deptMapper.selectById(7L)).thenReturn(null);
        UserRequest req = createRequest("bob", null);
        req.setId(5L);
        req.setDeptId(7L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("部门不存在", ex.getMessage());
    }

    @Test
    @DisplayName("修改用户：deptId 为空表示不修改部门")
    void updateKeepsDeptWhenNull() {
        SysUser existing = user(5L, "bob");
        existing.setDeptId(7L);
        when(userMapper.selectById(5L)).thenReturn(existing);
        UserRequest req = createRequest("bob", null);
        req.setId(5L);

        service.save(req);

        assertEquals(7L, existing.getDeptId());
    }

    // ---------------- 删除 / 重置密码 ----------------

    @Test
    @DisplayName("删除用户：内置管理员受保护")
    void deleteBuiltInAdmin() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(1L));

        assertEquals("内置管理员不可删除", ex.getMessage());
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("删除用户：用户不存在时抛出异常")
    void deleteMissingUser() {
        when(userMapper.selectById(5L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(5L));

        assertEquals("用户不存在", ex.getMessage());
        verify(userMapper, never()).deleteById(any(SysUser.class));
    }

    @Test
    @DisplayName("删除用户：清理角色关联并踢出会话")
    void deleteUser() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));

        service.delete(5L);

        verify(userRoleMapper).deleteByUserId(5L);
        verify(userMapper).deleteById(5L);
        verify(permissionCacheService).invalidateCache(List.of(5L));
    }

    @Test
    @DisplayName("重置密码：弱密码被策略拒绝")
    void resetWeakPassword() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "bob"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.resetPassword(5L, "abc"));

        assertEquals("密码长度不能少于 8 位", ex.getMessage());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("重置密码：加密保存、强制改密并踢出会话")
    void resetPassword() {
        SysUser existing = user(5L, "bob");
        when(userMapper.selectById(5L)).thenReturn(existing);

        service.resetPassword(5L, "Abc12345");

        assertEquals("ENC(Abc12345)", existing.getPassword());
        assertEquals(1, existing.getForceChangePassword().intValue());
        verify(userMapper).updateById(existing);
        verify(permissionCacheService).invalidateCache(List.of(5L));
        assertEquals("重置用户 bob 的密码", AuditContext.getDetailAndClear());
    }

    // ---------------- 查询辅助 ----------------

    @Test
    @DisplayName("查询用户角色：单个用户直接透传")
    void getRoleIdsOfUser() {
        when(userRoleMapper.findRoleIdsByUserId(5L)).thenReturn(Set.of(1L, 2L));

        assertEquals(Set.of(1L, 2L), service.getRoleIds(5L));
    }

    @Test
    @DisplayName("批量查询用户角色：空集合不查库")
    void getRoleIdsBatchEmpty() {
        assertTrue(service.getRoleIds(List.of()).isEmpty());
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    @DisplayName("批量查询用户角色：按用户分组，缺失用户返回空集合")
    void getRoleIdsBatch() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                relation(1L, 10L), relation(1L, 11L), relation(3L, 12L)));

        Map<Long, Set<Long>> result = service.getRoleIds(List.of(1L, 2L));

        assertEquals(Set.of(10L, 11L), result.get(1L));
        assertEquals(Set.of(), result.get(2L));
        assertEquals(Set.of(12L), result.get(3L));
    }

    @Test
    @DisplayName("角色下拉列表透传查询结果")
    void listAllRoles() {
        List<SysRole> roles = List.of(new SysRole());
        when(roleMapper.selectList(any())).thenReturn(roles);

        assertEquals(roles, service.listAllRoles());
    }

    @Test
    @DisplayName("查询用户：不存在时抛出异常")
    void getMissingUser() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.get(9L));
    }

    @Test
    @DisplayName("修改用户：邮箱等资料清空时记录差异")
    void updateClearsProfile() {
        SysUser existing = user(5L, "bob");
        existing.setEmail("bob@example.com");
        when(userMapper.selectById(5L)).thenReturn(existing);
        UserRequest req = createRequest("bob", null);
        req.setId(5L);
        req.setNickname("Nick-bob");

        service.save(req);

        assertNull(existing.getEmail());
        assertEquals("[email: bob@example.com -> null]", AuditContext.getDetailAndClear());
    }

    private static SysUserRole relation(long userId, long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
