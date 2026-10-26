package com.ping.user.pc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.config.CurrentUser;
import com.ping.user.pc.entity.SysLoginLog;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.LoginLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LoginLogController - 登录日志查询。
 *
 * @author humdeef
 */
@Tag(name = "登录日志")
@RestController
@RequestMapping("/user/pc/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;
    private final DataScopeService dataScopeService;
    private final CurrentUser currentUser;
    /** 分页查询登录日志。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:loginlog:list')")
    public ResponseEntity<ApiResponse<PageResult<SysLoginLog>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success) {
        DataScopeService.Scope scope = dataScopeService.resolve(currentUser.id());
        IPage<SysLoginLog> p = loginLogService.page(scope, page, size, username, success);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }
}
