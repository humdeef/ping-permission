package com.ping.user.pc.controller;

import com.ping.user.common.constant.UserConstants;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.ping.core.annotation.LoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import com.ping.core.dto.ApiResponse;
import com.ping.core.security.SecurityConstants;
import com.ping.user.auth.config.PingSecurityProperties;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.model.AuthConfigResponse;
import com.ping.user.pc.model.CaptchaResponse;
import com.ping.user.pc.model.ChangePasswordRequest;
import com.ping.user.pc.model.CurrentUserResponse;
import com.ping.user.pc.model.LoginRequest;
import com.ping.user.pc.model.LoginResponse;
import com.ping.user.pc.model.RegisterRequest;
import com.ping.user.auth.security.CaptchaService;
import com.ping.user.auth.security.LoginAttemptService;
import com.ping.user.pc.service.AuditLogService;
import com.ping.user.pc.service.AuthService;
import com.ping.user.pc.service.DeptService;
import com.ping.user.pc.service.LoginLogService;
import com.ping.user.pc.service.PermissionCacheService;
import com.ping.user.pc.service.PermissionService;
import com.ping.user.pc.service.OnlineUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AuthController - 认证控制器。
 *
 * @author humdeef
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/user/pc/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCacheService;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final LoginLogService loginLogService;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;
    private final PingSecurityProperties securityProperties;
    private final DeptService deptService;

    /**
     * 登录页公开配置（是否需要验证码、是否开放注册）。
     */
    @PublicApi
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AuthConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.ok(new AuthConfigResponse(
                securityProperties.getCaptcha().isEnabled(),
                securityProperties.isRegistrationEnabled())));
    }

    /**
     * 获取验证码（算术题，一次性使用）。
     */
    @PublicApi
    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaResponse>> captcha() {
        CaptchaService.Captcha captcha = captchaService.create();
        return ResponseEntity.ok(ApiResponse.ok(
                new CaptchaResponse(captcha.captchaId(), captcha.question())));
    }
    /** 后台用户登录，返回 token 和过期信息。 */
    @PublicApi
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest req,
                                                           HttpServletRequest request) {
        String username = req.getUsername().trim();

        // 1. 账号锁定检查
        if (loginAttemptService.isLocked(username)) {
            long minutes = Math.max(1, (loginAttemptService.remainingLockSeconds(username) + 59) / 60);
            loginLogService.recordCurrentRequest(null, username, false, "账号已锁定");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(429, "登录失败次数过多，请 " + minutes + " 分钟后再试"));
        }

        // 2. 验证码校验（启用后每次登录都需要）
        if (securityProperties.getCaptcha().isEnabled()
                && !captchaService.verify(req.getCaptchaId(), req.getCaptchaCode())) {
            loginLogService.recordCurrentRequest(null, username, false, "验证码错误");
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "验证码错误或已过期"));
        }

        SysUser user = authService.findByUsernameOrNull(username);
        // 用户不存在与密码错误统一返回 401，避免泄露账号是否存在
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            int remaining = loginAttemptService.recordFailure(username);
            Long userId = user == null ? null : user.getId();
            auditLogService.record(userId, username, "登录", "用户", userId, "用户名或密码错误", false);
            loginLogService.recordCurrentRequest(userId, username, false, "用户名或密码错误");
            if (remaining <= 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error(429, "登录失败次数过多，账号已锁定 "
                                + securityProperties.getLogin().getLockMinutes() + " 分钟"));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "用户名或密码错误，还可尝试 " + remaining + " 次"));
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            auditLogService.record(user.getId(), user.getUsername(), "登录", "用户", user.getId(), "账号已被禁用", false);
            loginLogService.recordCurrentRequest(user.getId(), username, false, "账号已被禁用");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, "账号已被禁用"));
        }

        // 3. 登录成功：签发 token、写入权限缓存、清理失败计数
        StpUtil.login(user.getId());
        permissionCacheService.cachePerms(user.getId());
        loginAttemptService.clear(username);
        boolean forceChangePassword = user.getForceChangePassword() != null && user.getForceChangePassword() == 1;
        // 网关据此拦截除改密/退出/当前用户外的所有接口
        StpUtil.getTokenSession().set("FORCE_PWD_CHANGE", forceChangePassword ? "1" : "0");
        // 审计字段使用名称快照，避免每次写业务表都跨服务查 sys_user
        StpUtil.getTokenSession().set(UserConstants.SESSION_NICKNAME,
                user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getUsername() : user.getNickname());
        // 在线会话中保存 IP/UA，供在线用户页展示
        StpUtil.getTokenSession().set(UserConstants.SESSION_IP, clientIp(request));
        StpUtil.getTokenSession().set(UserConstants.SESSION_USER_AGENT,
                truncate(request.getHeader("User-Agent"), 512));

        String token = StpUtil.getTokenValue();
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        long tokenTimeout = tokenInfo.getTokenTimeout();

        auditLogService.record(user.getId(), user.getUsername(), "登录", "用户", user.getId(), "登录成功", true);
        loginLogService.recordCurrentRequest(user.getId(), username, true, "登录成功");

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setTokenName(tokenInfo.getTokenName());
        resp.setTokenTimeout(tokenTimeout);
        resp.setTokenActiveTimeout(tokenInfo.getTokenActiveTimeout());
        resp.setExpiresAt(tokenTimeout > 0 ? System.currentTimeMillis() + tokenTimeout * 1000L : null);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setForceChangePassword(user.getForceChangePassword() != null && user.getForceChangePassword() == 1);
        // 同步刷新会话标记，保证网关拦截逻辑与数据库一致
        StpUtil.getTokenSession().set("FORCE_PWD_CHANGE", resp.isForceChangePassword() ? "1" : "0");
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    /**
     * 退出登录：服务端立即失效当前 token（前端同时清理本地缓存）。
     */
    @LoginOnlyApi
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Long userId = null;
        try {
            userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        } catch (Exception ignored) {
            // 已失效的 token 直接忽略
        }
        StpUtil.logout();
        if (userId != null) {
            auditLogService.record(userId, null, "退出登录", "用户", userId, "退出成功", true);
        }
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 当前用户修改密码。修改成功后当前 token 失效，需要重新登录。
     */
    @LoginOnlyApi
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody @Valid ChangePasswordRequest req) {
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        authService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        auditLogService.record(userId, null, "修改密码", "用户", userId, "修改成功", true);
        StpUtil.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 当前登录用户信息（含角色编码与权限编码），供前端做菜单/路由过滤。
     */
    @LoginOnlyApi
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
        return currentUserInfo();
    }

    /**
     * 获取后台系统当前用户信息。
     *
     * <p>与 /me 返回一致，语义更明确，供个人中心、顶栏等场景使用。</p>
     */
    @LoginOnlyApi
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> current() {
        return currentUserInfo();
    }

    private ResponseEntity<ApiResponse<CurrentUserResponse>> currentUserInfo() {
        if (!StpUtil.isLogin()) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录"));
        }
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        SysUser user = authService.findById(userId);
        if (user == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "用户不存在"));
        }
        // 用数据库中的最新权限刷新 token 会话，保证网关鉴权与前端菜单同步
        permissionCacheService.cachePerms(userId);
        StpUtil.getTokenSession().set(UserConstants.SESSION_NICKNAME,
                user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getUsername() : user.getNickname());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        long tokenTimeout = tokenInfo.getTokenTimeout();

        CurrentUserResponse resp = new CurrentUserResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setStatus(user.getStatus());
        resp.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            Map<Long, String> names = deptService.nameMap(java.util.Set.of(user.getDeptId()));
            resp.setDeptName(names.get(user.getDeptId()));
        }
        resp.setCreateTime(user.getCreateTime());
        resp.setForceChangePassword(user.getForceChangePassword() != null && user.getForceChangePassword() == 1);
        // 同步刷新会话标记，保证网关拦截逻辑与数据库一致
        StpUtil.getTokenSession().set("FORCE_PWD_CHANGE", resp.isForceChangePassword() ? "1" : "0");
        resp.setRoles(permissionCacheService.rolesOf(userId));
        resp.setPermissions(permissionCacheService.permissionsOf(userId));
        boolean admin = resp.getRoles() != null && resp.getRoles().contains(SecurityConstants.ADMIN_ROLE);
        resp.setMenus(permissionService.menuTree(userId, admin));
        resp.setTokenTimeout(tokenTimeout);
        resp.setTokenActiveTimeout(tokenInfo.getTokenActiveTimeout());
        resp.setExpiresAt(tokenTimeout > 0 ? System.currentTimeMillis() + tokenTimeout * 1000L : null);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
    /** 后台用户自助注册。 */
    @PublicApi
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SysUser>> register(@RequestBody @Valid RegisterRequest req) {
        SysUser user;
        try {
            user = authService.register(req.getUsername(), req.getPassword(),
                    req.getNickname(), req.getEmail(), req.getPhone());
        } catch (RuntimeException e) {
            auditLogService.record(null, req.getUsername(), "注册", "用户", null, e.getMessage(), false);
            throw e;
        }
        auditLogService.record(user.getId(), user.getUsername(), "注册", "用户", user.getId(), "用户注册", true);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
