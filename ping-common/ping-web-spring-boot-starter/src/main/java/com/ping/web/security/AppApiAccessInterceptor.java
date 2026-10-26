package com.ping.web.security;

import com.ping.core.annotation.AppLoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import com.ping.core.security.StpAppUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AppApiAccessInterceptor - {@code @AppLoginOnlyApi} 的服务内兜底校验。
 *
 * <p>校验 App 登录域（StpLogic("app")），与后台 {@code @LoginOnlyApi} 的
 * StpUtil 校验互不影响。</p>
 *
 * @author humdeef
 */
public class AppApiAccessInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean methodPublic = handlerMethod.hasMethodAnnotation(PublicApi.class);
        boolean methodLoginOnly = handlerMethod.hasMethodAnnotation(AppLoginOnlyApi.class);
        boolean classLoginOnly = handlerMethod.getBeanType().isAnnotationPresent(AppLoginOnlyApi.class);
        // 方法级 @PublicApi 优先于类级 @AppLoginOnlyApi
        boolean loginOnly = !methodPublic && (methodLoginOnly || classLoginOnly);
        if (loginOnly && !StpAppUtil.isLogin()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
            return false;
        }
        return true;
    }
}
