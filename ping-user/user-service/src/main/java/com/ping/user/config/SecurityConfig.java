package com.ping.user.config;

import com.ping.user.auth.config.PingSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SecurityConfig - 安全能力开关（异步、定时任务、配置绑定）。
 *
 * @author humdeef
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PingSecurityProperties.class)
public class SecurityConfig {
}
