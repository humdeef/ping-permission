package com.ping.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 放行路径配置
 *
 * @author humdeef
 */
@Data
@Component
@ConfigurationProperties(prefix = "satoken.permit")
public class PermitConfig {
    private List<String> paths = new ArrayList<>();
}
