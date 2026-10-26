package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.DeptService;
import com.ping.user.pc.service.DataScopeService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.entity.SysRoleDept;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleDeptMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.model.DeptRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DeptServiceImpl - 部门管理。
 *
 * <p>使用 ancestors 物化路径维护部门层级；移动部门时统一重算 ancestors。
 * 删除前校验是否存在子部门或已分配用户，避免产生孤儿数据。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {
    /** 部门数据访问 */
    private final SysDeptMapper deptMapper;
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 角色部门关系数据访问 */
    private final SysRoleDeptMapper roleDeptMapper;
    /** 数据权限服务 */
    private final DataScopeService dataScopeService;

    /**
     * 当前用户可见的部门：管理员全部；其他人按数据范围，
     * 并补全可见部门的祖先节点，保证前端能正常渲染层级。
     */
    @Override
    public List<SysDept> listVisible(Long userId) {
        List<SysDept> all = deptMapper.findAllOrdered();
        Set<Long> visible = dataScopeService.visibleDeptIds(userId);
        if (visible == null) {
            return all;
        }
        Map<Long, SysDept> byId = all.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Set<Long> withAncestors = new HashSet<>(visible);
        for (Long deptId : visible) {
            SysDept dept = byId.get(deptId);
            Long parentId = dept == null ? null : dept.getParentId();
            while (parentId != null && parentId != 0L && withAncestors.add(parentId)) {
                SysDept parent = byId.get(parentId);
                parentId = parent == null ? null : parent.getParentId();
            }
        }
        return all.stream()
                .filter(dept -> withAncestors.contains(dept.getId()))
                .collect(Collectors.toList());
    }
    /** {@inheritDoc} */
    @Override
    public SysDept get(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new RuntimeException("部门不存在");
        }
        return dept;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysDept save(DeptRequest req) {
        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        SysDept parent = null;
        if (parentId != 0L) {
            parent = get(parentId);
            if (parent.getStatus() != null && parent.getStatus() == 0) {
                throw new RuntimeException("父部门已禁用");
            }
        }

        SysDept dept;
        if (req.getId() != null) {
            dept = get(req.getId());
            checkCycle(dept.getId(), parentId);
        } else {
            dept = new SysDept();
            checkDuplicateName(parentId, req.getName(), null);
        }
        if (req.getId() != null) {
            checkDuplicateName(parentId, req.getName(), req.getId());
        }

        dept.setParentId(parentId);
        dept.setAncestors(parent == null ? "0" : parent.getAncestors() + "," + parent.getId());
        dept.setName(req.getName());
        dept.setCode(req.getCode());
        dept.setLeader(req.getLeader());
        dept.setPhone(req.getPhone());
        dept.setEmail(req.getEmail());
        dept.setSort(req.getSort() == null ? 0 : req.getSort());
        dept.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        if (req.getId() != null) {
            deptMapper.updateById(dept);
        } else {
            deptMapper.insert(dept);
        }
        rebuildAncestors();
        return dept;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDept dept = get(id);
        Long children = deptMapper.selectCount(new QueryWrapper<SysDept>().eq("parent_id", id));
        if (children != null && children > 0) {
            throw new RuntimeException("存在子部门，无法删除");
        }
        Long users = userMapper.selectCount(new QueryWrapper<SysUser>().eq("dept_id", id));
        if (users != null && users > 0) {
            throw new RuntimeException("部门下存在用户，无法删除");
        }
        roleDeptMapper.delete(new QueryWrapper<SysRoleDept>().eq("dept_id", id));
        deptMapper.deleteById(id);
        log.info("[Dept] 已删除部门 id={}, name={}", id, dept.getName());
    }

    /**
     * 部门 ID -> 名称，供用户列表/详情展示。
     */
    @Override
    public Map<Long, String> nameMap(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        return deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }

    /**
     * 校验父部门不能是自己或自己的下级。
     */
    private void checkCycle(Long id, Long parentId) {
        if (Objects.equals(id, parentId)) {
            throw new RuntimeException("父部门不能是自己");
        }
        Map<Long, SysDept> byId = deptMapper.findAllOrdered().stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && cursor != 0L && visited.add(cursor)) {
            if (Objects.equals(cursor, id)) {
                throw new RuntimeException("父部门不能是自己的下级");
            }
            SysDept node = byId.get(cursor);
            cursor = node == null ? null : node.getParentId();
        }
    }

    private void checkDuplicateName(Long parentId, String name, Long excludeId) {
        QueryWrapper<SysDept> wrapper = new QueryWrapper<SysDept>()
                .eq("parent_id", parentId)
                .eq("name", name)
                .ne(excludeId != null, "id", excludeId);
        Long count = deptMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new RuntimeException("同级部门名称已存在");
        }
    }

    /**
     * 根据父子关系重算全部部门的 ancestors（部门数量有限，成本可接受）。
     */
    private void rebuildAncestors() {
        List<SysDept> all = deptMapper.findAllOrdered();
        Map<Long, SysDept> byId = all.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Map<Long, String> computed = new HashMap<>(all.size());
        for (SysDept dept : all) {
            String ancestors = ancestorsOf(dept, byId, computed, new HashSet<>());
            if (!Objects.equals(ancestors, dept.getAncestors())) {
                dept.setAncestors(ancestors);
                deptMapper.updateById(dept);
            }
        }
    }

    private String ancestorsOf(SysDept dept, Map<Long, SysDept> byId,
                               Map<Long, String> computed, Set<Long> visiting) {
        if (computed.containsKey(dept.getId())) {
            return computed.get(dept.getId());
        }
        Long parentId = dept.getParentId();
        String result;
        if (parentId == null || parentId == 0L) {
            result = "0";
        } else {
            SysDept parent = byId.get(parentId);
            if (parent == null || !visiting.add(dept.getId())) {
                // 父节点缺失或检测到环：保留原值，避免死循环
                result = dept.getAncestors() == null ? "0" : dept.getAncestors();
            } else {
                String parentAncestors = ancestorsOf(parent, byId, computed, visiting);
                visiting.remove(dept.getId());
                result = parentAncestors + "," + parent.getId();
            }
        }
        computed.put(dept.getId(), result);
        return result;
    }
}
