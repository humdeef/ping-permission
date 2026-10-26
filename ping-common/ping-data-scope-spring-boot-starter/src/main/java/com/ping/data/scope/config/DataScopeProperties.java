package com.ping.data.scope.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据权限配置。
 *
 * @author humdeef
 */
@Data
@ConfigurationProperties(prefix = "ping.data-scope")
public class DataScopeProperties {

    /** 是否启用数据权限拦截；默认开启。 */
    private boolean enabled = true;
}
