package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.PermissionCacheService;

import cn.dev33.satoken.stp.StpUtil;
import com.ping.core.security.SecurityConstants;
import com.ping.user.pc.controller.AuthController;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysPermissionMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysRolePermissionMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PermissionCacheServiceImpl - 权限会话缓存统一管理
 *
 * <p>负责把用户的角色/权限编码写入 Sa-Token token 会话（Redis），
 * 并在权限关系发生任何变更时使缓存失效，保证鉴权数据与数据库一致。</p>
 *
 * <p>失效策略：踢出受影响用户的登录态，用户重新登录时由
 * {@link AuthController} 重新写入最新缓存。</p>
 *
 * @author humdeef
 * @date 2026/10/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheServiceImpl implements PermissionCacheService {
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色数据访问 */
    private final SysRoleMapper roleMapper;
    /** 角色权限关系数据访问 */
    private final SysRolePermissionMapper rolePermissionMapper;
    /** 权限数据访问 */
    private final SysPermissionMapper permissionMapper;

    /**
     * 登录时调用：把当前用户的角色/权限编码缓存进当前 token 会话（Redis）。
     */
    @Override
    public void cachePerms(Long userId) {
        try {
            Set<Long> roleIds = userRoleMapper.findRoleIdsByUserId(userId);
            List<String> roleCodes = roleCodesOf(roleIds);
            List<String> permCodes = permissionCodesOf(roleIds);
            StpUtil.getTokenSession().set("ROLES", String.join(",", roleCodes));
            StpUtil.getTokenSession().set("PERMS", String.join(",", permCodes));
            SysUser user = userMapper.selectById(userId);
            StpUtil.getTokenSession().set(SecurityConstants.SESSION_DEPT_ID,
                    user == null || user.getDeptId() == null ? "" : String.valueOf(user.getDeptId()));
            log.info("[PermCache] 已缓存 userId={}, roles={}, perms={}", userId, roleCodes, permCodes);
        } catch (Exception e) {
            log.warn("[PermCache] 缓存权限失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 查询用户的角色编码（实时查库，供 /auth/me 使用）。
     */
    @Override
    public List<String> rolesOf(Long userId) {
        return roleCodesOf(userRoleMapper.findRoleIdsByUserId(userId));
    }

    /**
     * 查询用户的权限编码（实时查库，供 /auth/me 使用）。
     */
    @Override
    public List<String> permissionsOf(Long userId) {
        return permissionCodesOf(userRoleMapper.findRoleIdsByUserId(userId));
    }

    /**
     * 权限变更时调用：踢出受影响用户的登录态，使其重新登录以刷新缓存。
     *
     * @param userIds 受影响的用户 ID
     */
    @Override
    public void invalidateCache(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        int kicked = 0;
        for (Long userId : userIds) {
            try {
                if (StpUtil.isLogin(userId)) {
                    StpUtil.kickout(userId);
                    kicked++;
                }
            } catch (Exception e) {
                log.warn("[PermCache] 踢出用户失败 userId={}: {}", userId, e.getMessage());
            }
        }
        log.info("[PermCache] 权限变更，已踢出 {} 个在线用户（共 {} 个受影响）: {}",
                kicked, userIds.size(), userIds);
    }

    private List<String> roleCodesOf(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getCode).collect(Collectors.toList());
    }

    private List<String> permissionCodesOf(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> permIds = rolePermissionMapper.findPermissionIdsByRoleIds(new ArrayList<>(roleIds));
        if (permIds == null || permIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permIds).stream()
                .map(SysPermission::getCode).collect(Collectors.toList());
    }
}
