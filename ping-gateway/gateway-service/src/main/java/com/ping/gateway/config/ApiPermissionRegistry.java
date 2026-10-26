package com.ping.gateway.config;

import com.ping.user.client.dto.ApiPermissionRuleDTO;
import com.ping.user.client.spi.PermissionDubboClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ApiPermissionRegistry - 接口权限规则注册表
 *
 * <p>同时维护两类规则：</p>
 * <ul>
 *     <li>白名单（whitelists）：来自 sys_api_whitelist 表中 status=1 的记录，
 *         匹配时直接放行，无需认证；刷新间隔短，以便白名单页修改后即时生效。</li>
 *     <li>鉴权规则（rules）：来自 sys_api_resource 表中 status=1 的记录，
 *         匹配时要求当前用户拥有对应权限编码；刷新间隔正常。</li>
 * </ul>
 *
 * @author humdeef
 * @date 2026/10/20
 */
@Slf4j
@Component
public class ApiPermissionRegistry {

    /** 精确路径优先，其次更长的模式优先，避免被泛化规则抢先命中 */
    private static final Comparator<ApiPermissionRuleDTO> RULE_PRIORITY = Comparator.comparingInt(
            (ApiPermissionRuleDTO r) -> hasWildcard(r.getPath()) ? 1 : 0).thenComparingInt(r -> -r.getPath().length());

    /** Ant 风格路径匹配器（与 SaTokenConfigure 保持一致） */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 任意 HTTP 方法。 */
    private static final String ANY_METHOD = "*";

    @DubboReference(check = false, timeout = 3000)
    private PermissionDubboClient permissionDubboClient;

    @Resource(name = "userRolePermThreadPool")
    private ExecutorService userRolePermThreadPool;

    /** 白名单（命中直接放行，不要求登录） */
    private final AtomicReference<List<ApiPermissionRuleDTO>> whitelists = new AtomicReference<>(List.of());

    /** 鉴权规则（命中则校验权限编码） */
    private final AtomicReference<List<ApiPermissionRuleDTO>> rules = new AtomicReference<>(List.of());

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refreshWhitelists();
        refreshRules();
    }

    /**
     * 刷新白名单；失败时保留上一批，避免鉴权突然全部放行。
     */
    @Scheduled(fixedDelayString = "${satoken.api-permissions.whitelist-refresh-ms:10000}")
    public void refreshWhitelists() {
        try {
            List<ApiPermissionRuleDTO> loaded = CompletableFuture.supplyAsync(permissionDubboClient::listEnabledWhitelists, userRolePermThreadPool)
                    .get(5, TimeUnit.SECONDS);
            if (loaded == null) {
                loaded = List.of();
            }
            loaded.sort(RULE_PRIORITY);
            whitelists.set(loaded);
            log.info("[SaToken] 白名单已刷新: {} 条", loaded.size());
        } catch (Exception e) {
            log.warn("[SaToken] 白名单刷新失败，沿用现有 {} 条: {}", whitelists.get().size(), e.getMessage());
        }
    }

    /**
     * 刷新鉴权规则；失败时保留上一批，避免鉴权规则突然失效。
     */
    @Scheduled(fixedDelayString = "${satoken.api-permissions.refresh-interval-ms:60000}")
    public void refreshRules() {
        try {
            List<ApiPermissionRuleDTO> loaded = CompletableFuture.supplyAsync(permissionDubboClient::listApiPermissionRules, userRolePermThreadPool)
                    .get(5, TimeUnit.SECONDS);
            if (loaded == null) {
                loaded = List.of();
            }
            loaded.sort(RULE_PRIORITY);
            rules.set(loaded);
            log.info("[SaToken] 接口权限规则已加载: {} 条", loaded.size());
        } catch (Exception e) {
            log.warn("[SaToken] 接口权限规则加载失败，沿用现有 {} 条规则: {}", rules.get().size(), e.getMessage());
        }
    }

    /**
     * 检查请求是否命中白名单；命中则返回 true（放行）。
     */
    public boolean isWhitelisted(String method, String path) {
        for (ApiPermissionRuleDTO rule : whitelists.get()) {
            if (rule.getPath() == null || rule.getPath().isBlank()) {
                continue;
            }
            if (matchesMethod(rule.getMethod(), method) && matchPath(rule.getPath(), path)) {
                return true;
            }
        }
        return false;
    }

    public List<ApiPermissionRuleDTO> getRules() {
        return rules.get();
    }

    public List<ApiPermissionRuleDTO> getWhitelists() {
        return whitelists.get();
    }

    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || ruleMethod.isBlank() || ANY_METHOD.equals(ruleMethod)) {
            return true;
        }
        return ruleMethod.equalsIgnoreCase(requestMethod);
    }

    /** Ant 风格路径匹配：支持 /user/auth/login 和 /api/public/** 等形式 */
    private boolean matchPath(String pattern, String path) {
        return PATH_MATCHER.match(pattern, path);
    }

    private static boolean hasWildcard(String path) {
        return path != null && (path.indexOf('*') >= 0 || path.contains("**"));
    }
}
