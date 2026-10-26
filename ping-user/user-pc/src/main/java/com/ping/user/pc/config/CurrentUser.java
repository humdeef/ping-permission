package com.ping.user.pc.config;

import cn.dev33.satoken.stp.StpUtil;
import com.ping.core.security.SecurityConstants;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * CurrentUser - 当前登录人上下文
 *
 * <p>{@code SaTokenAuthFilter} 已把角色编码（ROLE_xxx）与权限编码（如 system:role）
 * 注入 Spring Security 上下文，这里统一封装判断逻辑，避免各 Controller 重复实现。</p>
 *
 * @author humdeef
 * @date 2026/10/21
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final SysUserRoleMapper userRoleMapper;

    /** 当前登录用户 ID（Sa-Token loginId 即用户 ID） */
    public Long id() {
        return Long.valueOf(String.valueOf(StpUtil.getLoginId()));
    }

    public boolean isAdmin() {
        return hasAuthority("ROLE_" + SecurityConstants.ADMIN_ROLE);
    }

    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** 管理员，或拥有指定权限码 */
    public boolean canView(String authority) {
        return isAdmin() || hasAuthority(authority);
    }

    /** 当前用户拥有的角色 ID */
    public Set<Long> roleIds() {
        Set<Long> roleIds = userRoleMapper.findRoleIdsByUserId(id());
        return roleIds == null ? Collections.emptySet() : roleIds;
    }

    /**
     * 数据范围：管理员可操作任意用户，其他用户只能操作自己。
     */
    public void checkSelfOrAdmin(Long targetUserId) {
        if (!isAdmin() && !Objects.equals(targetUserId, id())) {
            throw new AccessDeniedException("无权访问其他用户");
        }
    }

    /**
     * 数据范围：管理员可操作任意角色，其他用户只能操作自己拥有的角色。
     */
    public void checkRoleAccess(Long roleId) {
        if (!isAdmin() && !roleIds().contains(roleId)) {
            throw new AccessDeniedException("无权访问该角色");
        }
    }
}
