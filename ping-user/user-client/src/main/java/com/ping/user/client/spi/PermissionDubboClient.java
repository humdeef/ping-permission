package com.ping.user.client.spi;

import com.ping.user.client.dto.ApiPermissionRuleDTO;
import com.ping.user.client.dto.UserInfoDTO;

/**
 * PermissionDubboClient - 用户权限 Dubbo 接口
 * 供网关等服务通过 Dubbo 调用
 *
 * @author humdeef
 * @date 2026/10/19
 */
public interface PermissionDubboClient {

    /**
     * 根据用户名查询用户信息（含角色和权限）。
     *
     * @param username 用户名
     * @return 用户信息
     */
    UserInfoDTO getUserInfoByUsername(String username);

    /**
     * 根据用户 ID 查询用户信息（含角色和权限）。
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    UserInfoDTO getUserInfoById(Integer id);

    /**
     * 校验用户是否有指定权限。
     *
     * @param username   用户名
     * @param permission 权限编码
     * @return 是否拥有该权限
     */
    boolean hasPermission(String username, String permission);

    /**
     * 校验用户是否有指定角色。
     *
     * @param username 用户名
     * @param role     角色编码
     * @return 是否拥有该角色
     */
    boolean hasRole(String username, String role);

    /**
     * 获取用户所有角色。
     *
     * @param username 用户名
     * @return 角色编码列表
     */
    java.util.List<String> getUserRoles(String username);

    /**
     * 获取用户所有权限。
     *
     * @param username 用户名
     * @return 权限编码列表
     */
    java.util.List<String> getUserPermissions(String username);

    /**
     * 获取全部「接口 -> 权限」规则（sys_permission 中 path 非空的记录）。
     *
     * @return 接口权限规则列表
     */
    java.util.List<ApiPermissionRuleDTO> listApiPermissionRules();

    /**
     * 获取全部启用的接口白名单（method 为 * 表示任意方法）。
     *
     * @return 白名单规则列表
     */
    java.util.List<ApiPermissionRuleDTO> listEnabledWhitelists();
}
