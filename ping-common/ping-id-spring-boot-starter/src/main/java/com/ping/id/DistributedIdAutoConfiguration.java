package com.ping.id;

import com.ping.core.id.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.InetAddress;

/**
 * DistributedIdAutoConfiguration - 内置雪花算法自动装配。
 *
 * <p>未配置 {@code ping.id.worker-id} 时按「主机名 + 应用名 + PID」推导，
 * 避免同一主机上的多个服务/实例得到相同的 workerId。
 * 生产环境仍建议显式配置唯一的 workerId。</p>
 *
 * @author humdeef
 */
@AutoConfiguration
@EnableConfigurationProperties(PingIdProperties.class)
public class DistributedIdAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DistributedIdAutoConfiguration.class);

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdGenerator distributedIdGenerator(PingIdProperties properties) {
        long workerId = properties.getWorkerId() >= 0 ? properties.getWorkerId() : deriveWorkerId();
        long datacenterId = properties.getDatacenterId() >= 0 ? properties.getDatacenterId() : 0L;
        log.info("[PingId] Snowflake workerId={}, datacenterId={}, epoch={}",
                workerId, datacenterId, properties.getEpoch());
        return new SnowflakeIdGenerator(workerId, datacenterId, properties.getEpoch());
    }

    /**
     * 按「主机名 + 应用名 + PID」推导 workerId；失败时按 PID 回退。
     */
    private long deriveWorkerId() {
        long bucketSize = SnowflakeIdGenerator.MAX_WORKER_ID + 1;
        long pid = ProcessHandle.current().pid();
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String seed = host + "|" + applicationName + "|" + pid;
            long workerId = Math.floorMod(seed.hashCode(), bucketSize);
            log.info("[PingId] workerId 未配置，自动推导: seed={}, workerId={}", seed, workerId);
            return workerId;
        } catch (Exception e) {
            long workerId = Math.floorMod(pid, bucketSize);
            log.warn("[PingId] 主机名推导失败，按 PID 回退: workerId={}", workerId);
            return workerId;
        }
    }
}
