package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.RoleService;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.PermissionCacheService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.core.security.SecurityConstants;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RoleServiceImpl - 角色管理
 *
 * <p>角色/权限/数据范围变更后，主动失效受影响用户的 Sa-Token 会话缓存，
 * 保证鉴权数据与数据库一致（权限变更即时生效）。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    /** 角色数据访问 */
    private final SysRoleMapper roleMapper;
    /** 角色权限关系数据访问 */
    private final SysRolePermissionMapper rolePermissionMapper;
    /** 角色部门关系数据访问 */
    private final SysRoleDeptMapper roleDeptMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 权限缓存服务 */
    private final PermissionCacheService permissionCacheService;
    /** {@inheritDoc} */
    @Override
    public IPage<SysRole> list(int page, int size, String name, String code) {
        Page<SysRole> p = new Page<>((long) Math.max(page, 0) + 1, size);
        if (name != null || code != null) {
            return roleMapper.search(p, name, code);
        }
        return roleMapper.selectPage(p, new QueryWrapper<SysRole>().orderByAsc("sort", "id"));
    }

    /**
     * 限定角色范围的分页查询（非管理员只能看到自己的角色）。
     */
    @Override
    public IPage<SysRole> list(Collection<Long> roleIds, int page, int size, String name, String code) {
        Page<SysRole> p = new Page<>((long) Math.max(page, 0) + 1, size);
        if (roleIds == null || roleIds.isEmpty()) {
            return p;
        }
        QueryWrapper<SysRole> wrapper = new QueryWrapper<SysRole>().in("id", roleIds);
        if (name != null && !name.isBlank()) {
            wrapper.like("name", name);
        }
        if (code != null && !code.isBlank()) {
            wrapper.like("code", code);
        }
        wrapper.orderByAsc("sort", "id");
        return roleMapper.selectPage(p, wrapper);
    }
    /** {@inheritDoc} 角色编码创建后不可修改；权限/数据范围变更后踢出该角色用户。 */
    @Override
    public SysRole get(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        return role;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole save(RoleRequest req) {
        SysRole role;
        SysRole before = null;
        if (req.getId() != null) {
            role = roleMapper.selectById(req.getId());
            if (role == null) {
                throw new RuntimeException("角色不存在");
            }
            before = copyForDiff(role);
            // 角色编码创建后不可修改，避免 sys_role_permission、sys_user_role 等关联语义漂移
            if (!role.getCode().equals(req.getCode())) {
                throw new RuntimeException("角色编码不可修改");
            }
        } else {
            Long count = roleMapper.selectCount(new QueryWrapper<SysRole>().eq("code", req.getCode()));
            if (count > 0) {
                throw new RuntimeException("角色编码已存在");
            }
            role = new SysRole();
            role.setCode(req.getCode());
        }
        role.setName(req.getName());
        role.setSort(req.getSort() == null ? 0 : req.getSort());
        role.setRemark(req.getRemark());
        // dataScope 为 null 表示本次不修改；新建角色默认「仅本人」（最小权限）
        if (req.getDataScope() != null) {
            role.setDataScope(normalizeDataScope(req.getDataScope()));
        } else if (req.getId() == null) {
            role.setDataScope(UserConstants.SCOPE_SELF);
        }
        if (req.getId() != null) {
            roleMapper.updateById(role);
        } else {
            try {
                roleMapper.insert(role);
            } catch (DuplicateKeyException e) {
                throw new RuntimeException("角色编码已存在");
            }
        }

        // 更新权限关联（permissionIds 为 null 表示本次不修改权限）
        if (req.getPermissionIds() != null) {
            rolePermissionMapper.deleteByRoleId(role.getId());
            long roleId = role.getId();
            List<SysRolePermission> entities = req.getPermissionIds().stream()
                    .map(pid -> {
                        SysRolePermission rp = new SysRolePermission();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(pid);
                        return rp;
                    })
                    .collect(Collectors.toList());
            for (SysRolePermission rp : entities) {
                rolePermissionMapper.insert(rp);
            }
        }

        // 自定义数据范围部门（deptIds 为 null 表示本次不修改）
        if (req.getDeptIds() != null) {
            roleDeptMapper.delete(new QueryWrapper<SysRoleDept>().eq("role_id", role.getId()));
            for (Long deptId : req.getDeptIds()) {
                SysRoleDept relation = new SysRoleDept();
                relation.setRoleId(role.getId());
                relation.setDeptId(deptId);
                roleDeptMapper.insert(relation);
            }
        }

        // 权限/数据范围变更：踢出该角色下所有用户的会话（强制重新登录以刷新权限缓存）
        invalidateRoleUsers(role.getId());
        AuditContext.setDetail(before == null
                ? "新增角色 code=" + role.getCode()
                : diff(before, role));
        return role;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        if (SecurityConstants.ADMIN_ROLE.equals(role.getCode())) {
            throw new RuntimeException("内置管理员角色不可删除");
        }
        // 先踢出该角色下的用户（此时关联还在）
        invalidateRoleUsers(id);
        // 清理角色-权限、角色-部门、用户-角色关联，避免孤儿数据
        rolePermissionMapper.deleteByRoleId(id);
        roleDeptMapper.delete(new QueryWrapper<SysRoleDept>().eq("role_id", id));
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("role_id", id));
        roleMapper.deleteById(id);
        AuditContext.setDetail("删除角色 " + role.getCode());
    }
    /** {@inheritDoc} */
    @Override
    public List<SysRole> listAll() {
        return roleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("sort", "id"));
    }
    /** {@inheritDoc} */
    @Override
    public List<SysRole> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(ids);
    }
    /** {@inheritDoc} */
    @Override
    public Set<Long> getPermissionIds(Long roleId) {
        return rolePermissionMapper.selectList(
                        new QueryWrapper<SysRolePermission>().eq("role_id", roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toSet());
    }
    /** {@inheritDoc} */
    @Override
    public Set<Long> getDeptIds(Long roleId) {
        return roleDeptMapper.selectList(
                        new QueryWrapper<SysRoleDept>().eq("role_id", roleId))
                .stream()
                .map(SysRoleDept::getDeptId)
                .collect(Collectors.toSet());
    }

    private int normalizeDataScope(Integer dataScope) {
        if (dataScope == null || dataScope < UserConstants.SCOPE_ALL
                || dataScope > UserConstants.SCOPE_CUSTOM) {
            return UserConstants.SCOPE_ALL;
        }
        return dataScope;
    }

    /**
     * 角色/权限变更后，找出该角色下的所有用户，踢出其会话。
     */
    private void invalidateRoleUsers(Long roleId) {
        List<Long> userIds = userRoleMapper.selectList(
                        new QueryWrapper<SysUserRole>().eq("role_id", roleId))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
        permissionCacheService.invalidateCache(userIds);
    }

    private SysRole copyForDiff(SysRole role) {
        SysRole copy = new SysRole();
        copy.setId(role.getId());
        copy.setCode(role.getCode());
        copy.setName(role.getName());
        copy.setSort(role.getSort());
        copy.setRemark(role.getRemark());
        copy.setDataScope(role.getDataScope());
        return copy;
    }

    private String diff(SysRole before, SysRole after) {
        StringBuilder sb = new StringBuilder("[");
        appendChange(sb, "code", before.getCode(), after.getCode());
        appendChange(sb, "name", before.getName(), after.getName());
        appendChange(sb, "sort", before.getSort(), after.getSort());
        appendChange(sb, "remark", before.getRemark(), after.getRemark());
        appendChange(sb, "dataScope", before.getDataScope(), after.getDataScope());
        if (sb.length() == 1) {
            sb.append("无字段变更");
        }
        return sb.append("]").toString();
    }

    private void appendChange(StringBuilder sb, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(field).append(": ").append(before).append(" -> ").append(after);
        }
    }
}
