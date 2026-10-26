package com.ping.web.security;

import com.ping.core.security.SecurityConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AuthorizationService - 统一的权限表达式实现。
 *
 * <p>供 Controller 使用：{@code @PreAuthorize("@authz.has('system:user:list')")}。
 * 超级管理员（角色 {@code admin} 或持有 {@code *:*:*} 通配权限）直接通过，
 * 避免在每个接口上重复写 {@code hasAnyRole('admin') or ...}。</p>
 *
 * @author humdeef
 */
public class AuthorizationService {

    private static final String ROLE_PREFIX = "ROLE_";

    /** 是否拥有指定权限编码（管理员始终为 true） */
    public boolean has(String permission) {
        if (isAdmin()) {
            return true;
        }
        if (permission == null || permission.isBlank()) {
            return false;
        }
        return hasAuthority(permission);
    }

    /** 是否拥有指定角色编码 */
    public boolean hasRole(String roleCode) {
        return roleCode != null && hasAuthority(ROLE_PREFIX + roleCode);
    }

    /** 是否超级管理员 */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> (ROLE_PREFIX + SecurityConstants.ADMIN_ROLE).equals(authority)
                        || SecurityConstants.SUPER_PERMISSION.equals(authority));
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
