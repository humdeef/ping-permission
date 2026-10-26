package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.DataScopeService;

import com.ping.core.security.SecurityConstants;
import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysRoleDept;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleDeptMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DataScopeServiceImpl - 数据权限解析。
 *
 * <p>支持 5 种数据范围（sys_role.data_scope）：</p>
 * <ol>
 *     <li>全部</li>
 *     <li>本部门</li>
 *     <li>本部门及下级</li>
 *     <li>仅本人</li>
 *     <li>自定义部门（sys_role_dept）</li>
 * </ol>
 * <p>用户拥有多个角色时取并集；任一角色为「全部」即拥有全部数据范围。</p>
 *
 * @author humdeef
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色数据访问 */
    private final SysRoleMapper roleMapper;
    /** 角色部门关系数据访问 */
    private final SysRoleDeptMapper roleDeptMapper;
    /** 部门数据访问 */
    private final SysDeptMapper deptMapper;

    /**
     * 当前用户的数据范围。
     */
    @Override
    public Scope resolve(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return new Scope(false, Set.of(), true, userId);
        }
        Set<Long> roleIds = userRoleMapper.findRoleIdsByUserId(userId);
        List<SysRole> roles = roleIds == null || roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectBatchIds(roleIds);

        boolean all = roles.stream().anyMatch(role ->
                SecurityConstants.ADMIN_ROLE.equals(role.getCode())
                        || Objects.equals(role.getDataScope(), UserConstants.SCOPE_ALL));
        if (all) {
            return new Scope(true, Set.of(), false, userId);
        }

        Set<Long> deptIds = new HashSet<>();
        for (SysRole role : roles) {
            int scope = role.getDataScope() == null ? UserConstants.SCOPE_SELF : role.getDataScope();
            if (scope == UserConstants.SCOPE_DEPT) {
                addDept(deptIds, user.getDeptId());
            } else if (scope == UserConstants.SCOPE_DEPT_AND_CHILD) {
                addDeptTree(deptIds, user.getDeptId());
            } else if (scope == UserConstants.SCOPE_CUSTOM) {
                deptIds.addAll(customDeptIds(role.getId()));
            }
            // 其余情况为仅本人：不添加部门
        }
        return new Scope(false, deptIds, deptIds.isEmpty(), userId);
    }

    /**
     * 是否可以访问目标用户。
     */
    @Override
    public boolean canAccessUser(Scope scope, SysUser target) {
        if (scope.all()) {
            return true;
        }
        if (target == null) {
            return false;
        }
        if (Objects.equals(scope.userId(), target.getId())) {
            return true;
        }
        return target.getDeptId() != null && scope.deptIds().contains(target.getDeptId());
    }

    /**
     * 部门页可见范围：返回 null 表示全部可见。
     */
    @Override
    public Set<Long> visibleDeptIds(Long userId) {
        Scope scope = resolve(userId);
        if (scope.all()) {
            return null;
        }
        if (!scope.deptIds().isEmpty()) {
            return scope.deptIds();
        }
        SysUser user = userMapper.selectById(userId);
        if (user != null && user.getDeptId() != null) {
            return Set.of(user.getDeptId());
        }
        return Set.of();
    }

    /**
     * 用户所在的部门及全部下级部门 ID。
     */
    @Override
    public Set<Long> deptAndChildren(Long deptId) {
        Set<Long> ids = new HashSet<>();
        addDeptTree(ids, deptId);
        return ids;
    }

    private void addDept(Set<Long> target, Long deptId) {
        if (deptId != null) {
            target.add(deptId);
        }
    }

    private void addDeptTree(Set<Long> target, Long deptId) {
        if (deptId == null) {
            return;
        }
        target.add(deptId);
        List<SysDept> all = deptMapper.findAllOrdered();
        String marker = "," + deptId + ",";
        for (SysDept dept : all) {
            String ancestors = "," + (dept.getAncestors() == null ? "" : dept.getAncestors()) + ",";
            if (ancestors.contains(marker)) {
                target.add(dept.getId());
            }
        }
    }

    private Set<Long> customDeptIds(Long roleId) {
        return roleDeptMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRoleDept>()
                                .eq("role_id", roleId))
                .stream().map(SysRoleDept::getDeptId).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 解析后的数据范围。
     */
}
