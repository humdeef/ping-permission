package com.ping.web.config;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.ttl.TtlRunnable;
import com.ping.web.exception.GlobalExceptionHandler;
import com.ping.web.security.ApiAccessInterceptor;
import com.ping.core.security.PermissionProvider;
import com.ping.web.security.AppApiAccessInterceptor;
import com.ping.web.security.AppTokenAuthFilter;
import com.ping.web.security.AuthorizationService;
import com.ping.web.security.SaTokenAuthFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CommonSecurityAutoConfiguration - servlet 微服务通用安全自动装配。
 *
 * <p>约定：HTTP 层请求统一放行（登录/权限由 Gateway 的 Sa-Token 校验），
 * 微服务内部通过 {@code @PreAuthorize} 做方法级兜底。</p>
 *
 * <p>WebFlux 服务（如网关）不会加载本配置。</p>
 *
 * @author humdeef
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, StpUtil.class})
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Import(GlobalExceptionHandler.class)
public class CommonSecurityAutoConfiguration {

    /** 放行全部 HTTP 请求，鉴权由网关 + 方法级注解完成 */
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** 通用密码编码器（BCrypt） */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Sa-Token 登录态桥接过滤器；PermissionProvider 可选 */
    @Bean
    @ConditionalOnMissingBean
    public SaTokenAuthFilter saTokenAuthFilter(ObjectProvider<PermissionProvider> permissionProvider) {
        return new SaTokenAuthFilter(permissionProvider.getIfAvailable());
    }

    /**
     * 统一权限表达式：{@code @PreAuthorize("@authz.has('system:user:list')")}。
     * 超级管理员直接通过，业务代码不再硬编码 hasAnyRole('admin')。
     */
    @Bean("authz")
    @ConditionalOnMissingBean(name = "authz")
    public AuthorizationService authorizationService() {
        return new AuthorizationService();
    }

    /** @LoginOnlyApi 的服务内登录兜底校验 */
    @Bean
    @ConditionalOnMissingBean
    public ApiAccessInterceptor apiAccessInterceptor() {
        return new ApiAccessInterceptor();
    }

    /** App 登录态桥接：全局生效，只有 App 登录时才注入认证信息 */
    @Bean
    @ConditionalOnMissingBean
    public AppTokenAuthFilter appTokenAuthFilter() {
        return new AppTokenAuthFilter();
    }

    /** @AppLoginOnlyApi 的服务内登录兜底校验 */
    @Bean
    @ConditionalOnMissingBean
    public AppApiAccessInterceptor appApiAccessInterceptor() {
        return new AppApiAccessInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "appApiAccessWebMvcConfigurer")
    public WebMvcConfigurer appApiAccessWebMvcConfigurer(AppApiAccessInterceptor appApiAccessInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(appApiAccessInterceptor).addPathPatterns("/**");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "apiAccessWebMvcConfigurer")
    public WebMvcConfigurer apiAccessWebMvcConfigurer(ApiAccessInterceptor apiAccessInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(apiAccessInterceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * 让 Spring 默认的 @Async 线程池自动传递用户上下文（ID/角色/权限）。
     * 应用已自定义 TaskDecorator 时不再注册，避免出现多个 TaskDecorator Bean。
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator userContextTaskDecorator() {
        return TtlRunnable::get;
    }
}
