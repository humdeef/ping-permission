package com.ping.user.pc.config;

import com.ping.core.security.PermissionProvider;
import com.ping.user.pc.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DbPermissionProvider - user-service 的权限数据来源实现（直接查库）。
 *
 * <p>通用过滤器读取 token 会话缓存；缓存缺失时通过本实现从数据库兜底。</p>
 *
 * @author humdeef
 */
@Component
@RequiredArgsConstructor
public class DbPermissionProvider implements PermissionProvider {

    private final PermissionCacheService permissionCacheService;

    @Override
    public List<String> roles(Long userId) {
        return permissionCacheService.rolesOf(userId);
    }

    @Override
    public List<String> permissions(Long userId) {
        return permissionCacheService.permissionsOf(userId);
    }
}
