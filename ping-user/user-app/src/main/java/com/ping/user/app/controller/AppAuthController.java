package com.ping.user.app.controller;

import com.ping.user.common.constant.UserConstants;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.ping.core.annotation.AppLoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import com.ping.core.dto.ApiResponse;
import com.ping.user.app.entity.AppUser;
import com.ping.user.app.model.AppLoginRequest;
import com.ping.user.app.model.AppLoginResponse;
import com.ping.user.app.model.AppRegisterRequest;
import com.ping.user.app.model.AppUserResponse;
import com.ping.core.security.StpAppUtil;
import com.ping.user.app.service.AppAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AppAuthController - C 端认证接口，统一前缀 /user/app。
 *
 * @author humdeef
 */
@Tag(name = "App 认证")
@RestController
@RequestMapping("/user/app/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;
    /** C 端用户注册。 */
    @PublicApi
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AppUserResponse>> register(
            @RequestBody @Valid AppRegisterRequest req) {
        AppUser user = appAuthService.register(req.getPhone(), req.getPassword(), req.getNickname());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }
    /** C 端用户登录。 */
    @PublicApi
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AppLoginResponse>> login(
            @RequestBody @Valid AppLoginRequest req) {
        AppUser user = appAuthService.login(req.getPhone(), req.getPassword());
        StpAppUtil.login(user.getId());
        StpAppUtil.LOGIC.getTokenSession().set(UserConstants.APP_PHONE, user.getPhone());
        StpAppUtil.LOGIC.getTokenSession().set(UserConstants.SESSION_NICKNAME,
                user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getPhone() : user.getNickname());

        SaTokenInfo tokenInfo = StpAppUtil.getTokenInfo();
        long tokenTimeout = tokenInfo.getTokenTimeout();

        AppLoginResponse resp = new AppLoginResponse();
        resp.setToken(StpAppUtil.getTokenValue());
        resp.setTokenName(tokenInfo.getTokenName());
        resp.setTokenTimeout(tokenTimeout);
        resp.setTokenActiveTimeout(tokenInfo.getTokenActiveTimeout());
        resp.setExpiresAt(tokenTimeout > 0 ? System.currentTimeMillis() + tokenTimeout * 1000L : null);
        resp.setUserId(String.valueOf(user.getId()));
        resp.setPhone(user.getPhone());
        resp.setNickname(user.getNickname());
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
    /** C 端用户退出登录。 */
    @AppLoginOnlyApi
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        StpAppUtil.logout();
        return ResponseEntity.ok(ApiResponse.ok());
    }
    /** 查询当前 App 用户信息。 */
    @AppLoginOnlyApi
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AppUserResponse>> me() {
        Long userId = Long.valueOf(String.valueOf(StpAppUtil.getLoginId()));
        AppUser user = appAuthService.findById(userId);
        if (user == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    private AppUserResponse toResponse(AppUser user) {
        AppUserResponse resp = new AppUserResponse();
        resp.setUserId(String.valueOf(user.getId()));
        resp.setPhone(user.getPhone());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatar());
        resp.setGender(user.getGender());
        resp.setStatus(user.getStatus());
        resp.setCreateTime(user.getCreateTime());
        return resp;
    }
}
