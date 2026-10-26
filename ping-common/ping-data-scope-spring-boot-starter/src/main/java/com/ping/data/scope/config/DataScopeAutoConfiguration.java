package com.ping.data.scope.config;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.ping.core.context.UserContextHolder;
import com.ping.data.scope.core.DataScopeProvider;
import com.ping.data.scope.mybatis.DataScopeHandler;
import com.ping.data.scope.web.DataScopeContextFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 数据权限自动装配。
 *
 * <p>各服务只需把 {@link DataScopeHandler} 放进自己的
 * {@code MybatisPlusInterceptor}（位于分页插件之前），并提供
 * {@link DataScopeProvider} 实现。</p>
 *
 * @author humdeef
 */
@AutoConfiguration
@ConditionalOnClass({DataPermissionInterceptor.class, UserContextHolder.class})
@ConditionalOnProperty(prefix = "ping.data-scope", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataScopeProperties.class)
public class DataScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataScopeHandler dataScopeHandler(DataScopeProperties properties,
                                             ObjectProvider<DataScopeProvider> providerProvider) {
        return new DataScopeHandler(properties, providerProvider);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<DataScopeContextFilter> dataScopeContextFilterRegistration() {
        FilterRegistrationBean<DataScopeContextFilter> registration =
                new FilterRegistrationBean<>(new DataScopeContextFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
