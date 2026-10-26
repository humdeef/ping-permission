package com.ping.web.security;

import com.ping.core.annotation.LoginOnlyApi;
import com.ping.core.annotation.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ApiAccessInterceptor - 注解的本地兜底校验。
 *
 * <p>{@code @LoginOnlyApi} 在服务内部也校验后台登录，防止绕过网关直连服务。
 * App 登录域由 {@code @AppLoginOnlyApi} 和 AppApiAccessInterceptor 处理。
 * {@code @PublicApi} 不做任何校验。</p>
 *
 * @author humdeef
 */
public class ApiAccessInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            // 方法级注解优先于类级注解；App 登录域由 AppApiAccessInterceptor 处理
            boolean methodPublic = handlerMethod.hasMethodAnnotation(PublicApi.class);
            boolean methodLoginOnly = handlerMethod.hasMethodAnnotation(LoginOnlyApi.class);
            boolean isPublic = methodPublic
                    || (!methodLoginOnly && handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class));
            boolean isLoginOnly = methodLoginOnly
                    || (!methodPublic && handlerMethod.getBeanType().isAnnotationPresent(LoginOnlyApi.class));
            if (isLoginOnly && !isPublic && !StpUtil.isLogin()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
                return false;
            }
        }
        return true;
    }
}
