package com.ping.user.pc.service.impl;

import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleDeptMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.model.DeptRequest;
import com.ping.user.pc.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 部门管理测试：ancestors 物化路径重算、层级环检测、删除前置校验。
 */
class DeptServiceImplTest {

    private SysDeptMapper deptMapper;
    private SysUserMapper userMapper;
    private SysRoleDeptMapper roleDeptMapper;
    private DataScopeService dataScopeService;
    private DeptServiceImpl service;

    @BeforeEach
    void setUp() {
        deptMapper = mock(SysDeptMapper.class);
        userMapper = mock(SysUserMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        dataScopeService = mock(DataScopeService.class);
        service = new DeptServiceImpl(deptMapper, userMapper, roleDeptMapper, dataScopeService);
    }

    private static SysDept dept(long id, long parentId, String ancestors) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setName("部门" + id);
        dept.setStatus(1);
        return dept;
    }

    @Test
    @DisplayName("可见部门：数据范围为全部时直接返回完整列表")
    void listVisibleAll() {
        List<SysDept> all = List.of(dept(1, 0, "0"), dept(2, 1, "0,1"));
        when(deptMapper.findAllOrdered()).thenReturn(all);
        when(dataScopeService.visibleDeptIds(100L)).thenReturn(null);

        assertEquals(all, service.listVisible(100L));
    }

    @Test
    @DisplayName("可见部门：补全祖先节点，保证前端能渲染层级")
    void listVisibleCompletesAncestors() {
        List<SysDept> all = List.of(
                dept(1, 0, "0"),
                dept(2, 1, "0,1"),
                dept(7, 1, "0,1"),
                dept(8, 7, "0,1,7"),
                dept(9, 8, "0,1,7,8"));
        when(deptMapper.findAllOrdered()).thenReturn(all);
        when(dataScopeService.visibleDeptIds(100L)).thenReturn(Set.of(8L));

        List<SysDept> visible = service.listVisible(100L);

        assertEquals(List.of(1L, 7L, 8L), visible.stream().map(SysDept::getId).toList());
    }

    @Test
    @DisplayName("部门不存在时抛出异常")
    void getMissingDept() {
        when(deptMapper.selectById(9L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.get(9L));

        assertEquals("部门不存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增根部门：ancestors 固定为 0")
    void createRootDept() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.findAllOrdered()).thenReturn(List.of());
        DeptRequest req = new DeptRequest();
        req.setName("研发部");

        SysDept saved = service.save(req);

        assertEquals(0L, saved.getParentId());
        assertEquals("0", saved.getAncestors());
        assertEquals(1, saved.getStatus().intValue());
        assertEquals(0, saved.getSort().intValue());
        verify(deptMapper).insert(saved);
    }

    @Test
    @DisplayName("新增子部门：ancestors 为父级路径 + 父 ID")
    void createChildDept() {
        when(deptMapper.selectById(2L)).thenReturn(dept(2, 1, "0,1"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.findAllOrdered()).thenReturn(List.of());
        DeptRequest req = new DeptRequest();
        req.setName("前端组");
        req.setParentId(2L);

        SysDept saved = service.save(req);

        assertEquals(2L, saved.getParentId());
        assertEquals("0,1,2", saved.getAncestors());
    }

    @Test
    @DisplayName("新增部门：父部门已禁用时拒绝")
    void createUnderDisabledParent() {
        SysDept disabled = dept(2, 1, "0,1");
        disabled.setStatus(0);
        when(deptMapper.selectById(2L)).thenReturn(disabled);
        DeptRequest req = new DeptRequest();
        req.setName("前端组");
        req.setParentId(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("父部门已禁用", ex.getMessage());
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    @DisplayName("新增部门：同级名称重复时拒绝")
    void createDuplicateName() {
        when(deptMapper.selectCount(any())).thenReturn(1L);
        DeptRequest req = new DeptRequest();
        req.setName("研发部");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("同级部门名称已存在", ex.getMessage());
    }

    @Test
    @DisplayName("移动部门：父部门不能是自己")
    void updateRejectsSelfParent() {
        when(deptMapper.selectById(2L)).thenReturn(dept(2, 1, "0,1"));
        DeptRequest req = new DeptRequest();
        req.setId(2L);
        req.setName("研发部");
        req.setParentId(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("父部门不能是自己", ex.getMessage());
    }

    @Test
    @DisplayName("移动部门：父部门不能是自己的下级")
    void updateRejectsDescendantParent() {
        SysDept node1 = dept(1, 0, "0");
        SysDept node2 = dept(2, 1, "0,1");
        SysDept node3 = dept(3, 2, "0,1,2");
        when(deptMapper.selectById(3L)).thenReturn(node3);
        when(deptMapper.selectById(1L)).thenReturn(node1);
        when(deptMapper.findAllOrdered()).thenReturn(List.of(node1, node2, node3));
        DeptRequest req = new DeptRequest();
        req.setId(1L);
        req.setName("总公司");
        req.setParentId(3L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(req));

        assertEquals("父部门不能是自己的下级", ex.getMessage());
    }

    @Test
    @DisplayName("移动部门：保存后重算 ancestors，修正失效的子孙路径")
    void updateRecalculatesAncestors() {
        SysDept node1 = dept(1, 0, "0");
        SysDept node2 = dept(2, 1, "0,1");
        SysDept node4 = dept(4, 2, "0,1,2");
        SysDept node5 = dept(5, 4, "0,0");
        when(deptMapper.selectById(2L)).thenReturn(node2);
        when(deptMapper.selectById(4L)).thenReturn(node4);
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.findAllOrdered()).thenReturn(List.of(node1, node2, node4, node5));
        DeptRequest req = new DeptRequest();
        req.setId(4L);
        req.setName("后端组");
        req.setParentId(2L);

        service.save(req);

        verify(deptMapper, times(2)).updateById(any(SysDept.class));
        assertEquals("0,1,2", node4.getAncestors());
        assertEquals("0,1,2,4", node5.getAncestors(), "子孙的 ancestors 应被重算");
    }

    @Test
    @DisplayName("删除部门：存在子部门时拒绝")
    void deleteWithChildren() {
        when(deptMapper.selectById(3L)).thenReturn(dept(3, 1, "0,1"));
        when(deptMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(3L));

        assertEquals("存在子部门，无法删除", ex.getMessage());
        verify(deptMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除部门：部门下有用户时拒绝")
    void deleteWithUsers() {
        when(deptMapper.selectById(3L)).thenReturn(dept(3, 1, "0,1"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(3L));

        assertEquals("部门下存在用户，无法删除", ex.getMessage());
        verify(deptMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除部门：清理角色部门关联后删除")
    void deleteRemovesRoleDept() {
        when(deptMapper.selectById(3L)).thenReturn(dept(3, 1, "0,1"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.delete(3L);

        verify(roleDeptMapper).delete(any());
        verify(deptMapper).deleteById(3L);
    }

    @Test
    @DisplayName("部门名称映射：空集合不查库")
    void nameMapEmpty() {
        assertTrue(service.nameMap(List.of()).isEmpty());
        verifyNoInteractions(deptMapper);
    }

    @Test
    @DisplayName("部门名称映射：按 ID 返回名称")
    void nameMap() {
        when(deptMapper.selectBatchIds(any())).thenReturn(List.of(dept(1, 0, "0"), dept(2, 1, "0,1")));

        Map<Long, String> names = service.nameMap(List.of(1L, 2L));

        assertEquals("部门1", names.get(1L));
        assertEquals("部门2", names.get(2L));
    }

    @Test
    @DisplayName("删除部门不存在的部门时抛出异常")
    void deleteMissingDept() {
        when(deptMapper.selectById(9L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.delete(9L));
        verify(deptMapper, never()).deleteById(anyLong());
    }
}
