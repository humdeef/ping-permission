package com.ping.user.pc.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.entity.SysRolePermission;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysApiResourceMapper;
import com.ping.user.pc.mapper.SysPermissionMapper;
import com.ping.user.pc.mapper.SysRolePermissionMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.pc.model.MenuNode;
import com.ping.user.pc.model.PermissionRequest;
import com.ping.user.pc.service.PermissionCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 权限管理测试：树构建、菜单过滤、保存校验与级联删除。
 */
class PermissionServiceImplTest {

    private SysPermissionMapper permissionMapper;
    private SysApiResourceMapper apiResourceMapper;
    private SysRolePermissionMapper rolePermissionMapper;
    private SysUserRoleMapper userRoleMapper;
    private PermissionCacheService permissionCacheService;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        permissionMapper = mock(SysPermissionMapper.class);
        apiResourceMapper = mock(SysApiResourceMapper.class);
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        permissionCacheService = mock(PermissionCacheService.class);
        service = new PermissionServiceImpl(permissionMapper, apiResourceMapper,
                rolePermissionMapper, userRoleMapper, permissionCacheService);
    }

    @AfterEach
    void clearAudit() {
        AuditContext.getDetailAndClear();
    }

    private static SysPermission perm(long id, String code, int type, long parentId, Integer sort) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName("权限" + id);
        permission.setType(type);
        permission.setParentId(parentId);
        permission.setSort(sort);
        permission.setVisible(1);
        return permission;
    }

    private static SysRolePermission rolePermission(long roleId, long permissionId) {
        SysRolePermission relation = new SysRolePermission();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        return relation;
    }

    private static SysUserRole userRole(long userId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        return relation;
    }

    // ---------------- 查询 ----------------

    @Test
    @DisplayName("分页查询：页码从 1 开始")
    void listPageNormalization() {
        when(permissionMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        IPage<SysPermission> page = service.list(0, 10, null, null);

        assertEquals(1, page.getCurrent());
        assertEquals(10, page.getSize());
    }

    @Test
    @DisplayName("限定角色范围：没有角色时不查库，返回空页")
    void listScopedWithoutRoles() {
        IPage<SysPermission> page = service.list(Set.of(), 0, 10, null, null);

        assertEquals(0, page.getRecords().size());
        verifyNoInteractions(permissionMapper);
    }

    @Test
    @DisplayName("限定角色范围：按角色拥有的权限 ID 过滤")
    void listScopedByRoles() {
        when(rolePermissionMapper.findPermissionIdsByRoleIds(anyList())).thenReturn(Set.of(5L));
        when(permissionMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        IPage<SysPermission> page = service.list(Set.of(10L), 0, 10, null, null);

        assertEquals(1, page.getCurrent());
        verify(permissionMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("角色权限 ID：空角色集合直接返回空集")
    void permissionIdsOfEmptyRoles() {
        assertEquals(Set.of(), service.permissionIdsOfRoles(null));
        assertEquals(Set.of(), service.permissionIdsOfRoles(Set.of()));
        verifyNoInteractions(rolePermissionMapper);
    }

    @Test
    @DisplayName("角色权限 ID：Mapper 返回 null 时按空集处理")
    void permissionIdsOfRolesNull() {
        when(rolePermissionMapper.findPermissionIdsByRoleIds(anyList())).thenReturn(null);

        assertEquals(Set.of(), service.permissionIdsOfRoles(Set.of(1L)));
    }

    @Test
    @DisplayName("角色权限 ID：透传 Mapper 结果")
    void permissionIdsOfRoles() {
        when(rolePermissionMapper.findPermissionIdsByRoleIds(anyList())).thenReturn(Set.of(1L, 2L));

        assertEquals(Set.of(1L, 2L), service.permissionIdsOfRoles(Set.of(9L)));
    }

    @Test
    @DisplayName("权限树：无权限时返回空列表")
    void listTreeWithoutPermissions() {
        assertTrue(service.listTreeByRoleIds(Set.of()).isEmpty());
        verifyNoInteractions(permissionMapper);
    }

    @Test
    @DisplayName("权限树：补全祖先节点后按原顺序返回")
    void listTreeCompletesAncestors() {
        when(rolePermissionMapper.findPermissionIdsByRoleIds(anyList())).thenReturn(Set.of(3L));
        when(permissionMapper.findAllOrdered()).thenReturn(List.of(
                perm(1, "a", 1, 0, 1),
                perm(2, "b", 2, 1, 1),
                perm(3, "c", 3, 2, 1),
                perm(4, "d", 2, 0, 1)));

        List<SysPermission> tree = service.listTreeByRoleIds(Set.of(9L));

        assertEquals(List.of(1L, 2L, 3L), tree.stream().map(SysPermission::getId).toList());
    }

    @Test
    @DisplayName("菜单树：管理员拿到全部目录和菜单，按钮被排除")
    void menuTreeForAdmin() {
        SysPermission menu = perm(2, "b", 2, 1, 1);
        menu.setVisible(null);
        SysPermission hidden = perm(4, "d", 2, 0, 1);
        hidden.setVisible(0);
        when(permissionMapper.findAllOrdered()).thenReturn(List.of(
                perm(1, "a", 1, 0, 1), menu, perm(3, "c", 3, 1, 1), hidden));

        List<MenuNode> roots = service.menuTree(1L, true);

        assertEquals(2, roots.size());
        assertEquals(1L, roots.get(0).getId());
        assertEquals(1, roots.get(0).getChildren().size());
        assertEquals(2L, roots.get(0).getChildren().get(0).getId());
        assertEquals(1, roots.get(0).getChildren().get(0).getVisible().intValue(),
                "visible 为空时默认显示");
        assertEquals(4L, roots.get(1).getId());
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    @DisplayName("菜单树：普通用户只拿到自己角色拥有的菜单及祖先目录")
    void menuTreeForUser() {
        when(userRoleMapper.findRoleIdsByUserId(100L)).thenReturn(Set.of(7L));
        when(rolePermissionMapper.findPermissionIdsByRoleIds(anyList())).thenReturn(Set.of(3L));
        when(permissionMapper.findAllOrdered()).thenReturn(List.of(
                perm(1, "a", 1, 0, 1),
                perm(2, "b", 2, 1, 1),
                perm(3, "c", 3, 2, 1),
                perm(4, "d", 2, 0, 1)));

        List<MenuNode> roots = service.menuTree(100L, false);

        assertEquals(1, roots.size());
        assertEquals(1L, roots.get(0).getId());
        assertEquals(1, roots.get(0).getChildren().size());
        assertEquals(2L, roots.get(0).getChildren().get(0).getId());
        assertTrue(roots.get(0).getChildren().get(0).getChildren().isEmpty(),
                "按钮权限不应出现在菜单树中");
    }

    @Test
    @DisplayName("菜单树：没有任何角色时返回空列表")
    void menuTreeWithoutRoles() {
        when(userRoleMapper.findRoleIdsByUserId(100L)).thenReturn(Set.of());
        when(permissionMapper.findAllOrdered()).thenReturn(List.of(perm(1, "a", 1, 0, 1)));

        assertTrue(service.menuTree(100L, false).isEmpty());
        verify(rolePermissionMapper, never()).findPermissionIdsByRoleIds(anyList());
    }

    @Test
    @DisplayName("按角色分组权限：空角色或无关联时返回空 Map")
    void permissionsByRoleIdsEmpty() {
        assertEquals(Map.of(), service.permissionsByRoleIds(null));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
        assertEquals(Map.of(), service.permissionsByRoleIds(Set.of(10L)));
    }

    @Test
    @DisplayName("按角色分组权限：每个角色内部按 sort、id 排序")
    void permissionsByRoleIdsSorted() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                rolePermission(10L, 1L), rolePermission(10L, 2L), rolePermission(20L, 2L)));
        when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(
                perm(1, "a", 3, 0, 2), perm(2, "b", 3, 0, 1)));

        Map<Long, List<SysPermission>> grouped = service.permissionsByRoleIds(Set.of(10L, 20L));

        assertEquals(List.of(2L, 1L), grouped.get(10L).stream().map(SysPermission::getId).toList());
        assertEquals(List.of(2L), grouped.get(20L).stream().map(SysPermission::getId).toList());
    }

    @Test
    @DisplayName("按角色分组权限：权限已被删除时跳过该关联")
    void permissionsByRoleIdsSkipsMissing() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(10L, 99L)));
        when(permissionMapper.selectBatchIds(any())).thenReturn(List.of());

        assertTrue(service.permissionsByRoleIds(Set.of(10L)).isEmpty());
    }

    @Test
    @DisplayName("多角色权限去重合并")
    void permissionsOfRolesDeduplicates() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                rolePermission(10L, 1L), rolePermission(20L, 1L)));
        when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(perm(1, "a", 3, 0, 1)));

        List<SysPermission> merged = service.permissionsOfRoles(Set.of(10L, 20L));

        assertEquals(1, merged.size());
    }

    @Test
    @DisplayName("完整权限列表透传 findAllOrdered")
    void listAllTree() {
        List<SysPermission> all = List.of(perm(1, "a", 1, 0, 1));
        when(permissionMapper.findAllOrdered()).thenReturn(all);

        assertEquals(all, service.listAllTree());
    }

    // ---------------- 保存 ----------------

    @Test
    @DisplayName("新增权限：编码重复时拒绝")
    void createDuplicateCode() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);
        PermissionRequest req = request("system:user:list");
        req.setPath("/users");
        req.setComponent("Users");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("权限编码已存在", ex.getMessage());
        verify(permissionMapper, never()).insert(any(SysPermission.class));
    }

    @Test
    @DisplayName("新增菜单：必须填写路由地址")
    void createMenuWithoutPath() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.save(request("system:user:list")));

        assertEquals("菜单类型必须填写路由地址", ex.getMessage());
    }

    @Test
    @DisplayName("新增菜单：路由地址必须以 / 开头")
    void createMenuWithRelativePath() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        PermissionRequest req = request("system:user:list");
        req.setPath("users");
        req.setComponent("Users");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("路由地址必须以 / 开头", ex.getMessage());
    }

    @Test
    @DisplayName("新增菜单：必须填写组件名")
    void createMenuWithoutComponent() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        PermissionRequest req = request("system:user:list");
        req.setPath("/users");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("菜单类型必须填写组件名", ex.getMessage());
    }

    @Test
    @DisplayName("新增按钮：清空路由与组件，不需要填写")
    void createButtonClearsPath() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        PermissionRequest req = request("system:user:add");
        req.setType(3);
        req.setPath("/users");
        req.setComponent("Users");

        SysPermission saved = service.save(req);

        assertEquals("", saved.getPath());
        assertEquals("", saved.getComponent());
    }

    @Test
    @DisplayName("新增菜单：路径与组件去空格，默认显示，踢出持有者会话")
    void createMenu() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(5L, 99L)));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L), userRole(7L), userRole(8L)));
        PermissionRequest req = request("system:user:list");
        req.setPath("/users ");
        req.setComponent(" Users ");

        SysPermission saved = service.save(req);

        assertEquals("/users", saved.getPath());
        assertEquals("Users", saved.getComponent());
        assertEquals(1, saved.getVisible().intValue());
        assertEquals(0, saved.getSort().intValue());
        verify(permissionMapper).insert(saved);
        verify(permissionCacheService).invalidateCache(List.of(7L, 8L));
        assertEquals("新增权限 code=system:user:list", AuditContext.getDetailAndClear());
    }

    @Test
    @DisplayName("新增权限：数据库唯一键冲突转换为业务异常")
    void createDuplicateKey() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_code")).when(permissionMapper)
                .insert(any(SysPermission.class));
        PermissionRequest req = request("system:user:list");
        req.setType(3);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("权限编码已存在", ex.getMessage());
    }

    @Test
    @DisplayName("修改权限：权限不存在时抛出异常")
    void updateMissingPermission() {
        when(permissionMapper.selectById(9L)).thenReturn(null);
        PermissionRequest req = request("a");
        req.setId(9L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("权限不存在", ex.getMessage());
    }

    @Test
    @DisplayName("修改权限：编码不可变更")
    void updateRejectsCodeChange() {
        when(permissionMapper.selectById(1L)).thenReturn(perm(1, "a", 1, 0, 1));
        PermissionRequest req = request("b");
        req.setId(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("权限编码不可修改", ex.getMessage());
        verify(permissionMapper, never()).updateById(any(SysPermission.class));
    }

    @Test
    @DisplayName("修改菜单：更新字段并踢出持有者会话")
    void updateMenu() {
        SysPermission existing = perm(1, "a", 2, 0, 1);
        existing.setPath("/old");
        existing.setComponent("Old");
        when(permissionMapper.selectById(1L)).thenReturn(existing);
        PermissionRequest req = request("a");
        req.setId(1L);
        req.setPath("/new");
        req.setComponent("New");

        service.save(req);

        assertEquals("/new", existing.getPath());
        assertEquals("New", existing.getComponent());
        verify(permissionMapper).updateById(existing);
        verify(permissionCacheService).invalidateCache(any());
        assertEquals("编辑权限 code=a", AuditContext.getDetailAndClear());
    }

    // ---------------- 删除 ----------------

    @Test
    @DisplayName("删除权限：权限不存在时抛出异常")
    void deleteMissingPermission() {
        when(permissionMapper.selectById(9L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(9L));

        assertEquals("权限不存在", ex.getMessage());
        verify(permissionMapper, never()).deleteBatchIds(any());
    }

    @Test
    @DisplayName("删除权限：级联删除子权限、角色关联与接口资源")
    void deleteCascades() {
        SysPermission root = perm(1, "P1", 1, 0, 1);
        SysPermission child = perm(2, "P2", 2, 1, 1);
        SysPermission grandChild = perm(3, "P3", 2, 2, 1);
        when(permissionMapper.selectById(1L)).thenReturn(root);
        when(permissionMapper.findAllOrdered()).thenReturn(List.of(
                root, child, grandChild, perm(5, "P5", 2, 0, 1)));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(5L, 1L)));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L), userRole(7L), userRole(8L)));

        service.delete(1L);

        verify(permissionCacheService).invalidateCache(List.of(7L, 8L));
        verify(rolePermissionMapper).delete(any());
        verify(apiResourceMapper).delete(any());
        verify(permissionMapper).deleteBatchIds(List.of(1L, 2L, 3L));
        assertEquals("删除权限 code=P1", AuditContext.getDetailAndClear());
    }

    private static PermissionRequest request(String code) {
        PermissionRequest req = new PermissionRequest();
        req.setCode(code);
        req.setName("权限");
        return req;
    }
}
