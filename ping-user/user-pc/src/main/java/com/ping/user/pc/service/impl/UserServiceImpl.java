package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.UserService;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.PermissionCacheService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysDeptMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.pc.model.UserRequest;
import com.ping.user.auth.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserServiceImpl - 用户管理
 *
 * <p>数据范围由 {@link DataScopeService} 统一解析；用户角色/密码变更后，
 * 主动失效该用户的 Sa-Token 会话，保证鉴权数据与数据库一致。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色数据访问 */
    private final SysRoleMapper roleMapper;
    /** 部门数据访问 */
    private final SysDeptMapper deptMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;
    /** 权限缓存服务 */
    private final PermissionCacheService permissionCacheService;
    /** 数据权限服务 */
    private final DataScopeService dataScopeService;
    /** 密码策略 */
    private final PasswordPolicy passwordPolicy;

    /**
     * 按数据范围分页查询用户，可按部门过滤。
     *
     * <p>数据范围由 {@code @DataScope} 在 SQL 层自动注入；这里的部门条件
     * 与数据范围条件始终是 AND 关系，传 deptId 不能绕过数据权限。</p>
     */
    @Override
    public IPage<SysUser> listScoped(int page, int size,
                                     String username, String nickname,
                                     Long deptId, boolean includeChildren) {
        QueryWrapper<SysUser> wrapper = searchWrapper(username, nickname);
        if (deptId != null) {
            Set<Long> deptIds = includeChildren
                    ? dataScopeService.deptAndChildren(deptId)
                    : Set.of(deptId);
            if (deptIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("dept_id", deptIds);
            }
        }
        wrapper.orderByDesc("id");
        return userMapper.selectScopedPage(new Page<>((long) Math.max(page, 0) + 1, size), wrapper);
    }

    private QueryWrapper<SysUser> searchWrapper(String username, String nickname) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like("username", username);
        }
        if (nickname != null && !nickname.isBlank()) {
            wrapper.like("nickname", nickname);
        }
        return wrapper;
    }
    /** {@inheritDoc} 管理员创建用户默认强制改密；内置管理员不可禁用或移除管理员角色。 */
    @Override
    public SysUser get(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser save(UserRequest req) {
        SysUser user;
        SysUser before = null;
        if (req.getId() != null) {
            user = requireUser(req.getId());
            validateAdminStatus(user, req);
            before = copyForDiff(user);
            applyPasswordChange(user, req);
        } else {
            user = buildNewUser(req);
        }

        applyProfile(user, req);
        if (req.getId() != null) {
            userMapper.updateById(user);
        } else {
            userMapper.insert(user);
        }
        Long savedUserId = user.getId();

        // 记录变更前的角色，用于对比是否发生变化
        Set<Long> oldRoleIds = req.getId() == null
                ? Collections.emptySet()
                : userRoleMapper.findRoleIdsByUserId(savedUserId);

        updateRoles(savedUserId, req.getRoleIds());
        invalidateSessions(savedUserId, req, oldRoleIds);

        AuditContext.setDetail(before == null
                ? "新增用户 id=" + savedUserId + ", username=" + user.getUsername()
                : diff(before, user));
        return user;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    private void validateAdminStatus(SysUser user, UserRequest req) {
        if (user.getId() == UserConstants.BUILT_IN_ADMIN_ID
                && req.getStatus() != null && req.getStatus() == 0) {
            throw new RuntimeException("内置管理员不可禁用");
        }
    }

    private SysUser buildNewUser(UserRequest req) {
        String username = req.getUsername() == null ? null : req.getUsername().trim();
        Long count = userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new RuntimeException("新增用户必须设置初始密码");
        }
        passwordPolicy.validate(req.getPassword());
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        // 管理员创建的账号首次登录需修改密码
        user.setForceChangePassword(1);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        return user;
    }

    private void applyPasswordChange(SysUser user, UserRequest req) {
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return;
        }
        // 修改密码：校验策略并强制下次登录改密
        passwordPolicy.validate(req.getPassword());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setForceChangePassword(1);
        user.setPasswordUpdatedAt(LocalDateTime.now());
    }

    private void applyProfile(SysUser user, UserRequest req) {
        user.setNickname(req.getNickname());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        } else if (req.getId() == null) {
            user.setStatus(1);
        }
        // deptId 为 null 表示不修改（新建时即不分配部门）
        if (req.getDeptId() != null || req.getId() == null) {
            if (req.getDeptId() != null && deptMapper.selectById(req.getDeptId()) == null) {
                throw new RuntimeException("部门不存在");
            }
            user.setDeptId(req.getDeptId());
        }
    }

    /**
     * 更新角色（roleIds 为 null 表示本次不修改角色）。
     */
    private void updateRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        // 内置管理员必须保留 admin 角色，避免误操作导致系统失去唯一管理员
        if (userId == UserConstants.BUILT_IN_ADMIN_ID) {
            SysRole adminRole = roleMapper.selectOne(new QueryWrapper<SysRole>()
                    .eq("code", com.ping.core.security.SecurityConstants.ADMIN_ROLE).last("LIMIT 1"));
            if (adminRole != null && !roleIds.contains(adminRole.getId())) {
                throw new RuntimeException("内置管理员必须保留超级管理员角色");
            }
        }
        userRoleMapper.deleteByUserId(userId);
        List<SysUserRole> entities = roleIds.stream().map(roleId -> {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            return ur;
        }).collect(Collectors.toList());
        for (SysUserRole ur : entities) {
            userRoleMapper.insert(ur);
        }
    }

    private void invalidateSessions(Long userId, UserRequest req, Set<Long> oldRoleIds) {
        // 角色变更：踢出该用户的会话（强制重新登录以刷新权限缓存）
        if (req.getId() != null && req.getRoleIds() != null) {
            Set<Long> newRoleIds = Set.copyOf(req.getRoleIds());
            if (!Objects.equals(oldRoleIds, newRoleIds)) {
                log.info("[UserService] 用户 {} 角色变更 {} -> {}，踢出会话", userId, oldRoleIds, newRoleIds);
                permissionCacheService.invalidateCache(List.of(userId));
            }
        }
        // 密码变更：踢出会话
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            permissionCacheService.invalidateCache(List.of(userId));
        }
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == UserConstants.BUILT_IN_ADMIN_ID) {
            throw new RuntimeException("内置管理员不可删除");
        }
        if (userMapper.selectById(id) == null) {
            throw new RuntimeException("用户不存在");
        }
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        permissionCacheService.invalidateCache(List.of(id));
    }

    /**
     * 管理员重置用户密码：下次登录强制改密，并踢出当前会话。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser user = get(id);
        passwordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(1);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        permissionCacheService.invalidateCache(List.of(id));
        AuditContext.setDetail("重置用户 " + user.getUsername() + " 的密码");
    }
    /** {@inheritDoc} */
    @Override
    public List<SysRole> listAllRoles() {
        return roleMapper.selectList(new QueryWrapper<>());
    }
    /** {@inheritDoc} */
    @Override
    public Set<Long> getRoleIds(Long userId) {
        return userRoleMapper.findRoleIdsByUserId(userId);
    }

    /**
     * 批量查询多个用户的角色 ID，避免列表页逐条查询。
     */
    @Override
    public Map<Long, Set<Long>> getRoleIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>(1);
        }
        Map<Long, Set<Long>> result = new HashMap<>(userIds.size());
        userIds.forEach(id -> result.put(id, new HashSet<>()));
        userRoleMapper.selectList(new QueryWrapper<SysUserRole>().in("user_id", userIds))
                .forEach(ur -> result.computeIfAbsent(ur.getUserId(), k -> new HashSet<>())
                        .add(ur.getRoleId()));
        return result;
    }

    private SysUser copyForDiff(SysUser user) {
        SysUser copy = new SysUser();
        copy.setId(user.getId());
        copy.setUsername(user.getUsername());
        copy.setNickname(user.getNickname());
        copy.setEmail(user.getEmail());
        copy.setPhone(user.getPhone());
        copy.setStatus(user.getStatus());
        copy.setDeptId(user.getDeptId());
        return copy;
    }

    private String diff(SysUser before, SysUser after) {
        StringBuilder sb = new StringBuilder("[");
        appendChange(sb, "username", before.getUsername(), after.getUsername());
        appendChange(sb, "nickname", before.getNickname(), after.getNickname());
        appendChange(sb, "email", before.getEmail(), after.getEmail());
        appendChange(sb, "phone", before.getPhone(), after.getPhone());
        appendChange(sb, "status", before.getStatus(), after.getStatus());
        appendChange(sb, "deptId", before.getDeptId(), after.getDeptId());
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
