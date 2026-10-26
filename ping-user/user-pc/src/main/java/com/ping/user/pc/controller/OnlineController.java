package com.ping.user.pc.controller;

import com.ping.core.dto.ApiResponse;
import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.model.OnlineUserDTO;
import com.ping.user.pc.service.OnlineUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OnlineController - 在线用户与强制下线。
 *
 * @author humdeef
 */
@Tag(name = "在线用户")
@RestController
@RequestMapping("/user/pc/online")
@RequiredArgsConstructor
public class OnlineController {

    private final OnlineUserService onlineUserService;
    /** 查询在线用户。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:online:list')")
    public ResponseEntity<ApiResponse<List<OnlineUserDTO>>> list(
            @RequestParam(required = false) String username) {
        return ResponseEntity.ok(ApiResponse.ok(onlineUserService.list(username)));
    }
    /** 强制指定 token 下线。 */
    @DeleteMapping("/{token}")
    @PreAuthorize("@authz.has('system:online:delete')")
    @Audit(action = "强制下线", targetType = "在线用户")
    public ResponseEntity<ApiResponse<Void>> kickout(@PathVariable String token) {
        onlineUserService.kickout(token);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
