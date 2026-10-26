package com.ping.gateway.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.ping.core.security.StpAppUtil;
import com.ping.user.client.dto.ApiPermissionRuleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SaTokenConfigure - Sa-Token 全局过滤器配置
 *
 * <p>网关统一完成认证与鉴权：</p>
 * <ol>
 *     <li>认证：未登录的请求直接拦截（放行白名单路径）</li>
 *     <li>鉴权：按「方法 + 路径」规则校验当前登录用户是否拥有对应权限编码</li>
 * </ol>
 *
 * <p>请求路径在进入匹配前先统一规范化（去重复斜杠、去末尾斜杠），
 * 避免由于路径写法差异导致规则匹配失效或被绕过。</p>
 *
 * @author humdeef
 * @date 2026/10/19
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfigure {

    private final PermitConfig permitConfig;
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final GatewaySecurityProperties securityProperties;
    private final AnnotatedApiRegistry annotatedApiRegistry;

    /** Ant 风格路径匹配器 */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 强制改密期间仍允许访问的接口 */
    private static final Set<String> FORCE_PASSWORD_ALLOWED = Set.of(
            "/user/pc/auth/password", "/user/pc/auth/logout", "/user/pc/auth/me");

    /** CORS 预检请求方法。 */
    private static final String HTTP_METHOD_OPTIONS = "OPTIONS";

    /** 任意 HTTP 方法。 */
    private static final String ANY_METHOD = "*";

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        List<String> excludes = new ArrayList<>();
        excludes.add("/favicon.ico");
        excludes.add("/actuator/**");

        if (permitConfig.getPaths() != null) {
            for (String path : permitConfig.getPaths()) {
                excludes.add(path.trim());
                log.info("[SaToken] 静态放行路径: {}", path.trim());
            }
        }
        String[] excludeArray = excludes.toArray(new String[0]);

        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(excludeArray)
                .setAuth(obj -> {
                    log.debug("Sa-Token 鉴权: {}", obj);
                    SaRouter.match("/**")
                            .notMatch(excludeArray)
                            .check(r -> {
                                String method = SaHolder.getRequest().getMethod();
                                String path = normalizePath(SaHolder.getRequest().getRequestPath());
                                // 预检请求直接放行，由 CorsWebFilter 处理
                                if (HTTP_METHOD_OPTIONS.equalsIgnoreCase(method)) {
                                    return;
                                }
                                // 0. 注解声明：@PublicApi 免登录直接放行
                                if (annotatedApiRegistry.isPublic(method, path)) {
                                    log.debug("[SaToken] 公开接口，放行: {} {}", method, path);
                                    return;
                                }
                                // 0.1 DB 白名单：直接放行，不要求登录
                                if (apiPermissionRegistry.isWhitelisted(method, path)) {
                                    log.debug("[SaToken] 命中白名单，放行: {} {}", method, path);
                                    return;
                                }
                                // 1. 认证：校验登录
                                // 1.0 注解声明：@AppLoginOnlyApi 使用 App 登录域（StpLogic("app")）
                                if (annotatedApiRegistry.isAppLoginOnly(method, path)) {
                                    StpAppUtil.checkLogin();
                                    log.debug("[SaToken] App 接口，仅校验 App 登录: {} {}", method, path);
                                    return;
                                }
                                StpUtil.checkLogin();
                                // 1.1 强制改密：除修改密码/退出/当前用户外，其余接口一律拒绝
                                if (isForcePasswordChange() && !FORCE_PASSWORD_ALLOWED.contains(path)) {
                                    throw new NotPermissionException("请先修改初始密码");
                                }
                                // 2. 注解声明：@LoginOnlyApi 仅要求登录，不校验权限
                                if (annotatedApiRegistry.isLoginOnly(method, path)) {
                                    log.debug("[SaToken] 移动端接口，仅校验登录: {} {}", method, path);
                                    return;
                                }
                                // 3. 鉴权：按数据库规则校验接口所需权限
                                checkApiPermission(method, path);
                            });
                })
                .setError(e -> {
                    int status = 500;
                    if (e instanceof NotLoginException) {
                        status = 401;
                    } else if (e instanceof NotPermissionException) {
                        status = 403;
                    }
                    SaHolder.getResponse().setStatus(status);
                    log.warn("Sa-Token 鉴权异常[{}]: {}", status, e.getMessage());
                    return SaResult.error(e.getMessage());
                });
    }

    /**
     * 按数据库规则校验当前请求所需权限。
     *
     * <p>默认拒绝（fail-closed）：</p>
     * <ul>
     *     <li>规则列表为空（加载失败）时拒绝；</li>
     *     <li>路径未命中任何规则时拒绝——新接口必须显式登记到 sys_api_resource，
     *         或使用 {@code @PublicApi}/{@code @LoginOnlyApi}/{@code @AppLoginOnlyApi} 声明策略，
     *         避免「忘记配置 = 任意登录用户可访问」。</li>
     * </ul>
     */
    private void checkApiPermission(String method, String path) {
        List<ApiPermissionRuleDTO> rules = apiPermissionRegistry.getRules();

        // fail-closed：规则加载失败时（空列表）拒绝所有非白名单请求
        if (securityProperties.isFailClosed() && rules.isEmpty()) {
            log.error("[SaToken] fail-closed 生效：鉴权规则为空，拒绝 {} {}", method, path);
            throw new NotPermissionException("鉴权规则未加载，拒绝访问");
        }

        for (ApiPermissionRuleDTO rule : rules) {
            if (rule.getPath() == null || rule.getPath().isBlank()) {
                continue;
            }
            if (matchesMethod(rule.getMethod(), method) && pathMatcher.match(rule.getPath(), path)) {
                StpUtil.checkPermission(rule.getCode());
                return;
            }
        }
        // 默认拒绝：未登记的接口不允许任何登录用户访问
        log.warn("[SaToken] 接口未配置权限规则，拒绝访问: {} {}", method, path);
        throw new NotPermissionException("接口未配置权限规则，拒绝访问");
    }

    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || ruleMethod.isBlank() || ANY_METHOD.equals(ruleMethod)) {
            return true;
        }
        return ruleMethod.equalsIgnoreCase(requestMethod);
    }

    /**
     * 当前 token 是否处于「首次登录/重置密码后必须改密」状态。
     * 登录与 /auth/me 会把该标记写入 token 会话。
     */
    private boolean isForcePasswordChange() {
        try {
            Object value = StpUtil.getTokenSession().get("FORCE_PWD_CHANGE");
            return value != null && "1".equals(String.valueOf(value));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 规范化请求路径（复用 {@link PathNormalizeWebFilter} 的规则，幂等）。
     */
    private String normalizePath(String path) {
        return PathNormalizeWebFilter.normalize(path);
    }
}
