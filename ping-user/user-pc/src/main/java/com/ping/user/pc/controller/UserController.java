package com.ping.user.pc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.config.CurrentUser;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.model.ResetPasswordRequest;
import com.ping.user.pc.model.UserDetailResponse;
import com.ping.user.pc.model.UserListItemDTO;
import com.ping.user.pc.model.UserRequest;
import com.ping.user.pc.service.ApiResourceService;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.DeptService;
import com.ping.user.pc.service.PermissionService;
import com.ping.user.pc.service.RoleService;
import com.ping.user.pc.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserController - 用户管理
 *
 * <p>接口级权限由网关按权限码校验；微服务侧再按权限码做二次校验。</p>
 *
 * <p>数据范围由 {@link DataScopeService} 统一解析（全部/本部门/本部门及下级/仅本人/自定义）。</p>
 *
 * <p>查看用户时的信息按当前登录人的权限逐层开放：</p>
 * <ul>
 *     <li>{@code system:role}：返回该用户的角色（roleIds/roleNames）</li>
 *     <li>{@code system:permission}：返回该用户所有角色的权限（permissions）</li>
 *     <li>{@code system:apiresource}：返回上述权限挂接的接口资源（resources）</li>
 * </ul>
 *
 * @author humdeef
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user/pc/users")
@RequiredArgsConstructor
public class UserController {

    private static final String AUTH_ROLE = "system:role:list";
    private static final String AUTH_PERMISSION = "system:permission:list";
    private static final String AUTH_API_RESOURCE = "system:apiresource:list";

    private final UserService userService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final ApiResourceService apiResourceService;
    private final DeptService deptService;
    private final DataScopeService dataScopeService;
    private final CurrentUser currentUser;
    /** 分页查询用户列表（按数据范围和部门过滤）。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:user:list')")
    public ResponseEntity<ApiResponse<PageResult<UserListItemDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "true") boolean includeChildren) {
        IPage<SysUser> p = userService.listScoped(page, size, username, nickname,
                deptId, includeChildren);
        List<SysUser> users = p.getRecords();

        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, Set<Long>> userRoleMap = userService.getRoleIds(userIds);
        Set<Long> allRoleIds = userRoleMap.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        Set<Long> deptIds = users.stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptService.nameMap(deptIds);

        boolean canViewRoles = currentUser.canView(AUTH_ROLE);
        boolean canViewPermissions = currentUser.canView(AUTH_PERMISSION);
        boolean canViewResources = currentUser.canView(AUTH_API_RESOURCE);

        // 角色信息仅在拥有角色管理权限时加载
        Map<Long, SysRole> roleById = new HashMap<>(allRoleIds.size());
        if (canViewRoles) {
            roleService.listByIds(allRoleIds).forEach(r -> roleById.put(r.getId(), r));
        }

        // 权限信息：查看权限或查看接口资源时需要（后者用于推导资源）
        Map<Long, List<SysPermission>> permissionsByRole = Map.of();
        boolean needPermissions = canViewPermissions || canViewResources;
        if (needPermissions && !allRoleIds.isEmpty()) {
            permissionsByRole = permissionService.permissionsByRoleIds(allRoleIds);
        }

        // 接口资源按权限编码分组
        Map<String, List<SysApiResource>> resourcesByPermissionCode = new HashMap<>(16);
        if (canViewResources) {
            Set<String> permissionCodes = permissionsByRole.values().stream()
                    .flatMap(List::stream)
                    .map(SysPermission::getCode)
                    .collect(Collectors.toSet());
            for (SysApiResource resource : apiResourceService.listByPermissionCodes(permissionCodes)) {
                resourcesByPermissionCode
                        .computeIfAbsent(resource.getPermissionCode(), k -> new ArrayList<>())
                        .add(resource);
            }
        }

        // 组装列表 DTO：无权限查看的字段保持 null，序列化时省略
        List<UserListItemDTO> list = new ArrayList<>();
        for (SysUser user : users) {
            Set<Long> roleIds = userRoleMap.getOrDefault(user.getId(), Collections.emptySet());
            UserListItemDTO dto = new UserListItemDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setDeptId(user.getDeptId());
            dto.setDeptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()));
            dto.setStatus(user.getStatus());

            if (canViewRoles) {
                dto.setRoleIds(new ArrayList<>(roleIds));
                dto.setRoleNames(roleIds.stream()
                        .map(roleById::get)
                        .filter(Objects::nonNull)
                        .map(SysRole::getName)
                        .collect(Collectors.toList()));
            }
            if (canViewPermissions) {
                dto.setPermissions(distinctPermissions(roleIds, permissionsByRole));
            }
            if (canViewResources) {
                dto.setResources(resourcesOf(distinctPermissions(roleIds, permissionsByRole),
                        resourcesByPermissionCode));
            }
            list.add(dto);
        }

        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(list, p.getTotal())));
    }
    /** 查询用户详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('system:user:list')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> get(@PathVariable Long id) {
        SysUser user = checkUserAccess(id);

        UserDetailResponse resp = new UserDetailResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setStatus(user.getStatus());
        resp.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            resp.setDeptName(deptService.nameMap(Set.of(user.getDeptId())).get(user.getDeptId()));
        }
        resp.setForceChangePassword(user.getForceChangePassword());
        resp.setPasswordUpdatedAt(user.getPasswordUpdatedAt());
        resp.setCreateTime(user.getCreateTime());

        Set<Long> roleIds = userService.getRoleIds(id);

        // 角色：需要角色管理权限
        if (currentUser.canView(AUTH_ROLE)) {
            resp.setRoleIds(roleIds);
            resp.setRoleNames(roleService.listByIds(roleIds).stream()
                    .map(SysRole::getName)
                    .collect(Collectors.toList()));
        }

        // 权限与接口资源：即使不能查看角色，也可按角色推导后返回
        boolean canViewPermissions = currentUser.canView(AUTH_PERMISSION);
        boolean canViewResources = currentUser.canView(AUTH_API_RESOURCE);
        List<SysPermission> permissions = List.of();
        if (canViewPermissions || canViewResources) {
            permissions = permissionService.permissionsOfRoles(roleIds);
        }
        if (canViewPermissions) {
            resp.setPermissions(permissions);
        }
        if (canViewResources) {
            Set<String> permissionCodes = permissions.stream()
                    .map(SysPermission::getCode)
                    .collect(Collectors.toSet());
            resp.setResources(apiResourceService.listByPermissionCodes(permissionCodes));
        }
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
    /** 新增用户。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:user:add')")
    @Audit(action = "新增用户", targetType = "用户")
    public ResponseEntity<ApiResponse<SysUser>> save(@RequestBody @Valid UserRequest req) {
        if (!currentUser.isAdmin()) {
            // 非管理员只能把用户建到自己可见的部门，且只能授予自己拥有的角色
            DataScopeService.Scope scope = dataScopeService.resolve(currentUser.id());
            checkDeptAccess(req.getDeptId(), scope);
            if (req.getRoleIds() != null && !currentUser.roleIds().containsAll(req.getRoleIds())) {
                throw new AccessDeniedException("不能授予超出自己权限范围的角色");
            }
            // 未指定角色时，默认沿用创建人自己的角色，避免创建出无任何菜单权限的账号
            if (req.getRoleIds() == null || req.getRoleIds().isEmpty()) {
                req.setRoleIds(new ArrayList<>(currentUser.roleIds()));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.save(req)));
    }
    /** 编辑用户。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:user:edit')")
    @Audit(action = "编辑用户", targetType = "用户")
    public ResponseEntity<ApiResponse<SysUser>> update(@PathVariable Long id,
                                                       @RequestBody @Valid UserRequest req) {
        SysUser target = checkUserAccess(id);
        req.setId(id);
        // 非管理员不能修改角色、部门、状态，也不能通过用户编辑接口改密码
        if (!currentUser.isAdmin()) {
            req.setRoleIds(null);
            req.setDeptId(target.getDeptId());
            req.setStatus(target.getStatus());
            req.setPassword(null);
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.save(req)));
    }
    /** 删除用户。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:user:delete')")
    @Audit(action = "删除用户", targetType = "用户")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        // 数据范围：只有管理员可以删除用户；且不能删除自己
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("只有管理员可以删除用户");
        }
        if (Objects.equals(id, currentUser.id())) {
            throw new RuntimeException("不能删除当前登录用户");
        }
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 管理员重置用户密码（下次登录强制修改）。
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@authz.has('system:user:resetpwd')")
    @Audit(action = "重置密码", targetType = "用户")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,
                                                           @RequestBody @Valid ResetPasswordRequest req) {
        checkUserAccess(id);
        userService.resetPassword(id, req.getPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
    /** 查询当前用户可分配的角色列表。 */
    @GetMapping("/roles/all")
    @PreAuthorize("@authz.has('system:role:list')")
    public ResponseEntity<ApiResponse<List<SysRole>>> listRoles() {
        List<SysRole> roles = currentUser.isAdmin()
                ? userService.listAllRoles()
                : roleService.listByIds(currentUser.roleIds());
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }

    // ---------------- 数据范围辅助 ----------------

    private SysUser checkUserAccess(Long id) {
        SysUser user = userService.get(id);
        DataScopeService.Scope scope = dataScopeService.resolve(currentUser.id());
        if (!dataScopeService.canAccessUser(scope, user)) {
            throw new AccessDeniedException("无权访问其他用户");
        }
        return user;
    }

    private void checkDeptAccess(Long deptId, DataScopeService.Scope scope) {
        if (deptId == null) {
            return;
        }
        if (!scope.all() && !scope.deptIds().contains(deptId)) {
            throw new AccessDeniedException("无权在该部门下创建用户");
        }
    }

    // ---------------- 权限可见性辅助 ----------------

    /** 多个角色合并后的权限（去重，按角色顺序稳定输出） */
    private List<SysPermission> distinctPermissions(Set<Long> roleIds,
                                                    Map<Long, List<SysPermission>> permissionsByRole) {
        Map<Long, SysPermission> merged = new LinkedHashMap<>();
        for (Long roleId : roleIds) {
            permissionsByRole.getOrDefault(roleId, List.of())
                    .forEach(p -> merged.putIfAbsent(p.getId(), p));
        }
        return new ArrayList<>(merged.values());
    }

    /** 权限挂接的接口资源（按权限编码匹配并去重） */
    private List<SysApiResource> resourcesOf(List<SysPermission> permissions,
                                             Map<String, List<SysApiResource>> resourcesByPermissionCode) {
        Map<Long, SysApiResource> merged = new LinkedHashMap<>();
        for (SysPermission permission : permissions) {
            resourcesByPermissionCode.getOrDefault(permission.getCode(), List.of())
                    .forEach(r -> merged.putIfAbsent(r.getId(), r));
        }
        return new ArrayList<>(merged.values());
    }
}
