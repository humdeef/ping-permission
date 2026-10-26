package com.ping.log.trace;

import com.ping.core.trace.BizTraceIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * BizTraceIdFilter - 业务链路 ID 过滤器。
 *
 * <p>优先读取请求头 {@code X-Biz-Tid}；没有则生成一个，写入 MDC（key: {@code bizTid}）
 * 并回写到响应头。logback 的 {@code %X{bizTid}} 即可打印。</p>
 *
 * <p>该过滤器由 ping-common 自动装配，所有 servlet 服务统一生效；
 * Dubbo 调用时由 common 的 {@code BizTraceConsumerFilter/ProviderFilter} 继续透传。</p>
 *
 * @author humdeef
 */
public class BizTraceIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String MDC_KEY = "bizTid";
    public static final String HEADER = "X-Biz-Tid";

    private final BizTraceIdGenerator generator;

    public BizTraceIdFilter(BizTraceIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String bizTid = request.getHeader(HEADER);
        if (bizTid == null || bizTid.isBlank()) {
            bizTid = generator.generate();
        }
        MDC.put(MDC_KEY, bizTid);
        response.setHeader(HEADER, bizTid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
