package com.ping.web.config;

import com.ping.web.security.AnnotatedApiScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * AnnotatedApiAutoConfiguration - 注解接口策略自动注册。
 *
 * <p>仅当服务同时具备 Spring MVC 与 Redis 时生效；把 {@code @LoginOnlyApi}/{@code @AppLoginOnlyApi}/{@code @PublicApi}
 * 的路径写入共享 Redis，供网关聚合。</p>
 *
 * @author humdeef
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({StringRedisTemplate.class, RequestMappingHandlerMapping.class})
public class AnnotatedApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AnnotatedApiScanner annotatedApiScanner(
            ObjectProvider<RequestMappingHandlerMapping> mappingProvider,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${spring.application.name:unknown}") String serviceName) {
        return new AnnotatedApiScanner(mappingProvider, redisProvider, serviceName);
    }
}
