package com.ping.user.pc.controller;

import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.config.CurrentUser;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.model.PermissionRequest;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.service.PermissionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PermissionController - 权限管理（菜单/按钮定义）
 *
 * <p>数据范围：管理员可查看/管理全部权限；拥有 {@code system:permission} 权限的
 * 普通用户只能看到自己角色拥有的权限（树接口会补全祖先节点）。</p>
 *
 * @author humdeef
 */
@Tag(name = "权限管理")
@RestController
@RequestMapping("/user/pc/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    /** 分页查询权限列表。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:permission:list')")
    public ResponseEntity<ApiResponse<PageResult<SysPermission>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {
        IPage<SysPermission> p = currentUser.isAdmin()
                ? permissionService.list(page, size, name, code)
                : permissionService.list(currentUser.roleIds(), page, size, name, code);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }
    /** 查询权限树。 */
    @GetMapping("/tree")
    @PreAuthorize("@authz.has('system:permission:list')")
    public ResponseEntity<ApiResponse<List<SysPermission>>> tree() {
        List<SysPermission> permissions = currentUser.isAdmin()
                ? permissionService.listAllTree()
                : permissionService.listTreeByRoleIds(currentUser.roleIds());
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }
    /** 新增权限。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:permission:add')")
    @Audit(action = "新增权限", targetType = "权限")
    public ResponseEntity<ApiResponse<SysPermission>> save(@RequestBody @Valid PermissionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.save(req)));
    }
    /** 编辑权限。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:permission:edit')")
    @Audit(action = "编辑权限", targetType = "权限")
    public ResponseEntity<ApiResponse<SysPermission>> update(@PathVariable Long id, @RequestBody @Valid PermissionRequest req) {
        req.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(permissionService.save(req)));
    }
    /** 删除权限。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:permission:delete')")
    @Audit(action = "删除权限", targetType = "权限")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
