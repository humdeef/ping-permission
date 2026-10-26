package com.ping.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * PathNormalizeWebFilter - 在网关最前置阶段规范化请求路径。
 *
 * <p>折叠连续斜杠、解析 {@code .}/{@code ..}、去除末尾斜杠，并<b>把规范化后的路径
 * 写回请求</b>，保证鉴权匹配的路径与实际转发给下游的路径完全一致，
 * 避免「鉴权按规范化路径、路由按原始路径」造成绕过或 404。</p>
 *
 * @author humdeef
 */
@Component
public class PathNormalizeWebFilter implements WebFilter, Ordered {

    /** 根路径。 */
    private static final String ROOT_PATH = "/";

    /** 路径分隔符。 */
    private static final String PATH_SEPARATOR = "/";

    /** 当前目录段。 */
    private static final String CURRENT_DIR = ".";

    /** 上级目录段。 */
    private static final String PARENT_DIR = "..";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String normalized = normalize(path);
        if (normalized.equals(path)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate().path(normalized).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        // 先于 Sa-Token 过滤器执行（SaReactorFilter 未实现 Ordered，默认最低优先级）
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 路径规范化：分段解析 {@code .}/{@code ..}，折叠空段。
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return ROOT_PATH;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split(PATH_SEPARATOR)) {
            if (segment.isEmpty() || CURRENT_DIR.equals(segment)) {
                continue;
            }
            if (PARENT_DIR.equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? ROOT_PATH : PATH_SEPARATOR + String.join(PATH_SEPARATOR, segments);
    }
}
