package com.ping.core.security;

import java.util.List;

/**
 * PermissionProvider - 用户角色/权限数据来源 SPI。
 *
 * <p>通用过滤器优先读取 token 会话缓存；缓存缺失（旧 token）时调用本接口兜底。
 * 各微服务可基于自己的数据源实现：</p>
 * <ul>
 *     <li>user-service：查库（角色关联表 + 权限表）</li>
 *     <li>order-service 等：通过 Dubbo 调 user-service</li>
 * </ul>
 *
 * @author humdeef
 */
public interface PermissionProvider {

    /**
     * 查询用户角色编码。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> roles(Long userId);

    /**
     * 查询用户权限编码。
     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    List<String> permissions(Long userId);
}
