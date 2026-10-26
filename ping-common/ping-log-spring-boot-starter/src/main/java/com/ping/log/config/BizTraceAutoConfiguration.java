package com.ping.log.config;

import com.ping.core.id.DistributedIdGenerator;
import com.ping.log.trace.BizTraceIdFilter;
import com.ping.core.trace.BizTraceIdGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

/**
 * BizTraceAutoConfiguration - 业务链路 ID 自动装配（所有 servlet 服务）。
 *
 * <p>优先使用 common-id starter 提供的 {@link DistributedIdGenerator}；
 * 未引入时回退到 UUID。</p>
 *
 * @author humdeef
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BizTraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BizTraceIdGenerator bizTraceIdGenerator(
            ObjectProvider<DistributedIdGenerator> distributedIdGenerator) {
        DistributedIdGenerator distributed = distributedIdGenerator.getIfAvailable();
        if (distributed != null) {
            return distributed::nextId;
        }
        return () -> UUID.randomUUID().toString().replace("-", "");
    }

    @Bean
    @ConditionalOnMissingBean
    public BizTraceIdFilter bizTraceIdFilter(BizTraceIdGenerator generator) {
        return new BizTraceIdFilter(generator);
    }
}
