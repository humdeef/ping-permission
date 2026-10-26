package com.ping.user.pc.controller;

import com.ping.core.dto.ApiResponse;
import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.config.CurrentUser;
import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.model.DeptRequest;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.DeptService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * DeptController - 部门管理。
 *
 * @author humdeef
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/user/pc/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;
    private final DataScopeService dataScopeService;
    private final CurrentUser currentUser;
    /** 查询当前用户可见的部门树。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:dept:list')")
    public ResponseEntity<ApiResponse<List<SysDept>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(deptService.listVisible(currentUser.id())));
    }
    /** 查询部门详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('system:dept:list')")
    public ResponseEntity<ApiResponse<SysDept>> get(@PathVariable Long id) {
        checkVisible(id);
        return ResponseEntity.ok(ApiResponse.ok(deptService.get(id)));
    }
    /** 新增部门。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:dept:add')")
    @Audit(action = "新增部门", targetType = "部门")
    public ResponseEntity<ApiResponse<SysDept>> save(@RequestBody @Valid DeptRequest req) {
        checkVisible(req.getParentId());
        return ResponseEntity.ok(ApiResponse.ok(deptService.save(req)));
    }
    /** 编辑部门。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:dept:edit')")
    @Audit(action = "编辑部门", targetType = "部门")
    public ResponseEntity<ApiResponse<SysDept>> update(@PathVariable Long id,
                                                       @RequestBody @Valid DeptRequest req) {
        checkVisible(id);
        checkVisible(req.getParentId());
        req.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(deptService.save(req)));
    }
    /** 删除部门。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:dept:delete')")
    @Audit(action = "删除部门", targetType = "部门")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        checkVisible(id);
        deptService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private void checkVisible(Long deptId) {
        if (deptId == null || deptId == 0L) {
            return;
        }
        Set<Long> visible = dataScopeService.visibleDeptIds(currentUser.id());
        if (visible != null && !visible.contains(deptId)) {
            throw new AccessDeniedException("无权访问该部门");
        }
    }
}
