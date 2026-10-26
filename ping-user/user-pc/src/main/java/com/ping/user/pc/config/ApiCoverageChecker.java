package com.ping.user.pc.config;

import com.ping.core.annotation.AppLoginOnlyApi;
import com.ping.core.annotation.LoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysApiWhitelist;
import com.ping.user.pc.service.ApiResourceService;
import com.ping.user.pc.service.WhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ApiCoverageChecker - 接口权限覆盖巡检。
 *
 * <p>网关已改为「未命中规则默认拒绝」。本巡检在服务启动后扫描所有 Controller，
 * 找出既没有注解声明、也没有登记到 sys_api_resource / sys_api_whitelist 的接口，
 * 以 WARN 日志提示，避免上线后才发现接口被网关拦截。</p>
 *
 * @author humdeef
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCoverageChecker {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 任意请求方法。 */
    private static final String ANY_METHOD = "*";

    /** 浏览器图标路径。 */
    private static final String FAVICON_PATH = "/favicon.ico";

    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;
    private final ApiResourceService apiResourceService;
    private final WhitelistService whitelistService;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
        if (mapping == null) {
            return;
        }
        List<SysApiResource> resources = apiResourceService.listEnabled();
        List<SysApiWhitelist> whitelists = whitelistService.listAll().stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 1)
                .toList();

        List<String> uncovered = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (isAnnotated(handler)) {
                continue;
            }
            for (String pattern : entry.getKey().getPatternValues()) {
                if (isIgnored(pattern)) {
                    continue;
                }
                for (String method : methodNames(entry.getKey())) {
                    if (!covered(method, pattern, resources, whitelists)) {
                        uncovered.add(method + " " + pattern);
                    }
                }
            }
        }
        if (uncovered.isEmpty()) {
            log.info("[ApiCoverage] 所有接口均已登记权限规则或注解策略");
        } else {
            log.warn("[ApiCoverage] 发现 {} 个未登记策略的接口（网关默认拒绝），请补充 sys_api_resource 或 @PublicApi/@LoginOnlyApi/@AppLoginOnlyApi: {}",
                    uncovered.size(), uncovered);
        }
    }

    private boolean isAnnotated(HandlerMethod handler) {
        boolean methodPublic = handler.hasMethodAnnotation(PublicApi.class);
        boolean methodLoginOnly = handler.hasMethodAnnotation(LoginOnlyApi.class)
                || handler.hasMethodAnnotation(AppLoginOnlyApi.class);
        boolean classPublic = handler.getBeanType().isAnnotationPresent(PublicApi.class);
        boolean classLoginOnly = handler.getBeanType().isAnnotationPresent(LoginOnlyApi.class)
                || handler.getBeanType().isAnnotationPresent(AppLoginOnlyApi.class);
        return methodPublic || methodLoginOnly || classPublic || classLoginOnly;
    }

    private boolean covered(String method, String pattern,
                            List<SysApiResource> resources, List<SysApiWhitelist> whitelists) {
        for (SysApiWhitelist whitelist : whitelists) {
            if (matchesMethod(whitelist.getMethod(), method)
                    && PATH_MATCHER.match(whitelist.getPath(), pattern)) {
                return true;
            }
        }
        for (SysApiResource resource : resources) {
            if (matchesMethod(resource.getMethod(), method)
                    && PATH_MATCHER.match(resource.getPath(), pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || ruleMethod.isBlank()) {
            return true;
        }
        if (ANY_METHOD.equals(ruleMethod)) {
            return true;
        }
        return ruleMethod.equalsIgnoreCase(requestMethod);
    }

    private List<String> methodNames(RequestMappingInfo info) {
        var methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return List.of(ANY_METHOD);
        }
        return methods.stream().map(RequestMethod::name).toList();
    }

    private boolean isIgnored(String pattern) {
        return pattern.startsWith("/actuator")
                || pattern.startsWith("/error")
                || FAVICON_PATH.equals(pattern);
    }
}
