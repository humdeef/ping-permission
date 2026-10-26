package com.ping.user.pc.controller;

import com.ping.user.pc.annotation.Audit;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.model.WhitelistRequest;
import com.ping.user.pc.entity.SysApiWhitelist;
import com.ping.user.pc.service.WhitelistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * WhitelistController - 接口白名单控制器。
 *
 * @author humdeef
 */
@Tag(name = "接口白名单")
@RestController
@RequestMapping("/user/pc/whitelist")
@RequiredArgsConstructor
public class WhitelistController {

    private final WhitelistService whitelistService;
    /** 分页查询白名单。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:whitelist:list')")
    public ResponseEntity<ApiResponse<PageResult<SysApiWhitelist>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String path) {
        IPage<SysApiWhitelist> p = whitelistService.list(page, size, path);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }
    /** 新增白名单。 */
    @PostMapping
    @PreAuthorize("@authz.has('system:whitelist:add')")
    @Audit(action = "新增白名单", targetType = "白名单")
    public ResponseEntity<ApiResponse<SysApiWhitelist>> save(@RequestBody @Valid WhitelistRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(whitelistService.save(req)));
    }
    /** 编辑白名单。 */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('system:whitelist:edit')")
    @Audit(action = "编辑白名单", targetType = "白名单")
    public ResponseEntity<ApiResponse<SysApiWhitelist>> update(@PathVariable Long id, @RequestBody @Valid WhitelistRequest req) {
        req.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(whitelistService.save(req)));
    }
    /** 删除白名单。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('system:whitelist:delete')")
    @Audit(action = "删除白名单", targetType = "白名单")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        whitelistService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
