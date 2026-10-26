package com.ping.user.pc.controller;

import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.config.CurrentUser;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.model.RoleDetailResponse;
import com.ping.user.pc.model.RoleRequest;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * RoleController - 角色管理
 *
 * <p>数据范围：管理员可查看/管理全部角色；拥有 {@code system:role} 权限的普通用户
 * 只能看到自己拥有的角色。角色详情中的权限列表（permissionIds）需要
 * {@code system:permission} 权限才会返回。</p>
 *
 * @author humdeef
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/user/pc/roles")
@RequiredArgsConstructor
public class RoleController {

    /** 权限列表查看权限码。 */
    private static final String PERM_PERMISSION_LIST = "system:permission:list";

    /** 部门列表查看权限码。 */
    private static final String PERM_DEPT_LIST = "system:dept:list";

    private final RoleService roleService;
    private final CurrentUser currentUser;
    /** 分页查询角色列表。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:role:list')")
    public ResponseEntity<ApiResponse<PageResult<SysRole>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {
        IPage<SysRole> p = currentUser.isAdmin()
                ? roleService.list(page, size, name, code)
                : roleService.list(currentUser.roleIds(), page, size, name, code);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }
    /** 查询角色详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('system:role:list')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> get(@PathVariable Long id) {
        currentUser.checkRoleAccess(id);
        SysRole role = roleService.get(id);
        RoleDetailResponse resp = new RoleDetailResponse();
        resp.setRole(role);
        // 角色拥有的权限：需要权限管理权限
        if (currentUser.canView(PERM_PERMISSION_LIST)) {
            Set<Long> permissionIds = roleService.getPermissionIds(id);
            resp.setPermissionIds(permissionIds);
        }
        // 自定义数据范围部门：需要部门查看权限
        if (currentUser.isAdmin() || currentUser.canView(PERM_DEPT_LIST)) {
            resp.setDeptIds(roleService.getDeptIds(id));
        }
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
    /** 新增角色。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:role:add')")
    @Audit(action = "新增角色", targetType = "角色")
    public ResponseEntity<ApiResponse<SysRole>> save(@RequestBody @Valid RoleRequest req) {
        restrictPermissionAssignment(req);
        return ResponseEntity.ok(ApiResponse.ok(roleService.save(req)));
    }
    /** 编辑角色。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:role:edit')")
    @Audit(action = "编辑角色", targetType = "角色")
    public ResponseEntity<ApiResponse<SysRole>> update(@PathVariable Long id, @RequestBody @Valid RoleRequest req) {
        currentUser.checkRoleAccess(id);
        req.setId(id);
        restrictPermissionAssignment(req);
        return ResponseEntity.ok(ApiResponse.ok(roleService.save(req)));
    }
    /** 删除角色。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:role:delete')")
    @Audit(action = "删除角色", targetType = "角色")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        currentUser.checkRoleAccess(id);
        if (currentUser.roleIds().contains(id)) {
            throw new RuntimeException("不能删除当前用户拥有的角色");
        }
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    /** 查询当前用户可见的全部角色。 */
    @GetMapping("/all")
    @PreAuthorize("@authz.has('system:role:list')")
    public ResponseEntity<ApiResponse<List<SysRole>>> listAll() {
        List<SysRole> roles = currentUser.isAdmin()
                ? roleService.listAll()
                : roleService.listByIds(currentUser.roleIds());
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }

    /**
     * 非管理员不能分配权限，也不能调整数据范围（防止越权查看全量数据）。
     */
    private void restrictPermissionAssignment(RoleRequest req) {
        if (!currentUser.isAdmin()) {
            req.setPermissionIds(null);
            req.setDataScope(null);
            req.setDeptIds(null);
        }
    }
}
