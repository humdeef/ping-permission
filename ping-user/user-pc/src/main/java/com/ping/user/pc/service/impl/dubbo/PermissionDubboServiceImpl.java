package com.ping.user.pc.service.impl.dubbo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ping.user.client.spi.PermissionDubboClient;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysPermissionMapper;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysRolePermissionMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.client.dto.ApiPermissionRuleDTO;
import com.ping.user.client.dto.UserInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PermissionDubboServiceImpl - 用户权限 Dubbo 服务实现
 *
 * <p>角色与权限数据全部来自数据库（sys_user / sys_role / sys_permission 及关联表）。</p>
 *
 * @author humdeef
 * @date 2026/10/19
 */
@DubboService
@Slf4j
@RequiredArgsConstructor
public class PermissionDubboServiceImpl implements PermissionDubboClient {

    /** 权限通配后缀。 */
    private static final String WILDCARD_SUFFIX = ":*";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final com.ping.user.pc.mapper.SysApiResourceMapper apiResourceMapper;
    private final com.ping.user.pc.mapper.SysApiWhitelistMapper whitelistMapper;

    @Override
    public UserInfoDTO getUserInfoByUsername(String username) {
        log.info("PermissionDubboServiceImpl getUserInfoByUsername: {}", username);
        return toUserInfo(findByUsername(username));
    }

    @Override
    public UserInfoDTO getUserInfoById(Integer id) {
        log.info("PermissionDubboServiceImpl getUserInfoById: {}", id);
        if (id == null) {
            return null;
        }
        return toUserInfo(userMapper.selectById(id.longValue()));
    }

    @Override
    public boolean hasPermission(String username, String permission) {
        return getUserPermissions(username).stream().anyMatch(granted -> matches(granted, permission));
    }

    @Override
    public boolean hasRole(String username, String role) {
        return getUserRoles(username).contains(role);
    }

    @Override
    public List<String> getUserRoles(String username) {
        SysUser user = findByUsername(username);
        if (user == null) {
            return Collections.emptyList();
        }
        return listRoleCodes(listRoleIds(user.getId()));
    }

    @Override
    public List<String> getUserPermissions(String username) {
        SysUser user = findByUsername(username);
        if (user == null) {
            return Collections.emptyList();
        }
        return listPermissionCodes(listRoleIds(user.getId()));
    }

    @Override
    public List<ApiPermissionRuleDTO> listApiPermissionRules() {
        // 鉴权规则来自独立的接口资源表，与权限（菜单/按钮）定义解耦
        List<com.ping.user.pc.entity.SysApiResource> resources = apiResourceMapper.selectList(
                new QueryWrapper<com.ping.user.pc.entity.SysApiResource>()
                        .eq("status", 1)
                        .orderByAsc("sort", "id"));
        return resources.stream()
                .map(r -> new ApiPermissionRuleDTO(
                        r.getPermissionCode(),
                        r.getMethod() == null || r.getMethod().isBlank() ? "*" : r.getMethod(),
                        r.getPath()))
                .collect(Collectors.toList());
    }

    private SysUser findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
    }

    private Set<Long> listRoleIds(Long userId) {
        Set<Long> roleIds = userRoleMapper.findRoleIdsByUserId(userId);
        return roleIds == null ? Collections.emptySet() : roleIds;
    }

    private List<String> listRoleCodes(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getCode).collect(Collectors.toList());
    }

    private List<String> listPermissionCodes(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> permissionIds = rolePermissionMapper.findPermissionIdsByRoleIds(new ArrayList<>(roleIds));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysPermission> permissions = permissionMapper.selectBatchIds(permissionIds);
        return permissions.stream().map(SysPermission::getCode).collect(Collectors.toList());
    }

    private UserInfoDTO toUserInfo(SysUser user) {
        if (user == null) {
            return null;
        }
        Set<Long> roleIds = listRoleIds(user.getId());
        return new UserInfoDTO(
                user.getId().intValue(),
                user.getUsername(),
                null,
                null,
                null,
                listRoleCodes(roleIds),
                listPermissionCodes(roleIds));
    }

    /**
     * 权限匹配：支持精确匹配与 {@code xxx:*} 通配（如 system:* 匹配 system:user:add）。
     */
    private boolean matches(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        if (granted.equals(required)) {
            return true;
        }
        if (granted.endsWith(WILDCARD_SUFFIX)) {
            return required.startsWith(granted.substring(0, granted.length() - WILDCARD_SUFFIX.length()));
        }
        return false;
    }

    @Override
    public List<ApiPermissionRuleDTO> listEnabledWhitelists() {
        List<com.ping.user.pc.entity.SysApiWhitelist> enabled = whitelistMapper.selectList(
                new QueryWrapper<com.ping.user.pc.entity.SysApiWhitelist>()
                        .eq("status", 1));
        return enabled.stream()
                .map(w -> new ApiPermissionRuleDTO(
                        null,
                        w.getMethod() == null || w.getMethod().isBlank() ? "*" : w.getMethod(),
                        w.getPath()))
                .collect(Collectors.toList());
    }
}
