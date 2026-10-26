package com.ping.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关鉴权安全策略配置
 *
 * <p>对应配置前缀 {@code satoken.api-permissions}。</p>
 *
 * @author humdeef
 */
@Data
@Component
@ConfigurationProperties(prefix = "satoken.api-permissions")
public class GatewaySecurityProperties {

    /**
     * 失败即关闭模式：当鉴权规则列表为空（通常因加载失败）时，
     * 是否拒绝所有非白名单请求。默认 true。
     *
     * <p>生产环境务必保持 true，避免规则加载失败绕过鉴权。</p>
     */
    private boolean failClosed = true;

    /** 鉴权规则（sys_permission）刷新间隔（毫秒） */
    private long refreshIntervalMs = 60000;

    /** 白名单（sys_api_whitelist）刷新间隔（毫秒） */
    private long whitelistRefreshMs = 10000;
}
