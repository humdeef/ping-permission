package com.ping.user.pc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.config.CurrentUser;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.model.ApiResourceRequest;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.service.ApiResourceService;
import com.ping.user.pc.service.PermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ApiResourceController - 接口资源（网关鉴权规则）管理
 *
 * <p>数据范围：管理员可查看/管理全部资源；拥有 {@code system:apiresource} 权限的
 * 普通用户只能看到自己角色权限挂接的接口资源。</p>
 *
 * @author humdeef
 */
@Tag(name = "接口资源")
@RestController
@RequestMapping("/user/pc/api-resources")
@RequiredArgsConstructor
public class ApiResourceController {

    private final ApiResourceService apiResourceService;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    /** 分页查询接口资源。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:apiresource:list')")
    public ResponseEntity<ApiResponse<PageResult<SysApiResource>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String permissionCode) {
        IPage<SysApiResource> p = currentUser.isAdmin()
                ? apiResourceService.list(page, size, path, permissionCode)
                : apiResourceService.list(currentPermissionCodes(), page, size, path, permissionCode);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }
    /** 查询全部接口资源。 */
    @GetMapping("/all")
    @PreAuthorize("@authz.has('system:apiresource:list')")
    public ResponseEntity<ApiResponse<List<SysApiResource>>> all() {
        List<SysApiResource> resources = currentUser.isAdmin()
                ? apiResourceService.listAll()
                : apiResourceService.listAll(currentPermissionCodes());
        return ResponseEntity.ok(ApiResponse.ok(resources));
    }
    /** 新增接口资源。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:apiresource:add')")
    @Audit(action = "新增接口资源", targetType = "接口资源")
    public ResponseEntity<ApiResponse<SysApiResource>> save(@RequestBody @Valid ApiResourceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(apiResourceService.save(req)));
    }
    /** 编辑接口资源。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:apiresource:edit')")
    @Audit(action = "编辑接口资源", targetType = "接口资源")
    public ResponseEntity<ApiResponse<SysApiResource>> update(@PathVariable Long id, @RequestBody @Valid ApiResourceRequest req) {
        req.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(apiResourceService.save(req)));
    }
    /** 删除接口资源。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:apiresource:delete')")
    @Audit(action = "删除接口资源", targetType = "接口资源")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        apiResourceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 当前用户角色拥有的权限编码集合 */
    private Set<String> currentPermissionCodes() {
        return permissionService.permissionsOfRoles(currentUser.roleIds()).stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toSet());
    }
}
