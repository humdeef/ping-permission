package com.ping.user.pc.service;

import java.util.List;

/**
 * 用户角色/权限会话缓存管理。
 *
 * @author humdeef
 */
public interface PermissionCacheService {

    /**
     * 把用户的角色和权限编码写入当前 token 会话。
     *
     * @param userId 用户 ID
     */
    void cachePerms(Long userId);

    /**
     * 实时查询用户的角色编码。
     *
     * @param userId 用户 ID
     * @return 列表
     */
    List<String> rolesOf(Long userId);

    /**
     * 实时查询用户的权限编码。
     *
     * @param userId 用户 ID
     * @return 列表
     */
    List<String> permissionsOf(Long userId);

    /**
     * 权限变更后踢出受影响用户，使其重新登录刷新缓存。
     *
     * @param userIds 用户 ID 集合
     */
    void invalidateCache(List<Long> userIds);
}
