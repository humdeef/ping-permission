package com.ping.log.config;

import com.ping.log.trace.BizTraceIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * BizTraceFeignAutoConfiguration - 把 bizTid 透传到 Feign 下游（仅在引入 Feign 时生效）。
 *
 * @author humdeef
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BizTraceFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "bizTraceIdRequestInterceptor")
    public RequestInterceptor bizTraceIdRequestInterceptor() {
        return template -> {
            String bizTid = MDC.get(BizTraceIdFilter.MDC_KEY);
            if (bizTid != null && !bizTid.isBlank()) {
                template.header(BizTraceIdFilter.HEADER, bizTid);
            }
        };
    }
}
