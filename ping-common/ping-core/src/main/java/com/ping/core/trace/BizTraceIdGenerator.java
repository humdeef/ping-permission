package com.ping.core.trace;

/**
 * BizTraceIdGenerator - 业务链路 ID 生成器 SPI。
 *
 * <p>公共模块默认使用 UUID；服务可提供自己的 Bean 覆盖
 * （例如 order-service 使用 ping-id starter 的雪花算法）。</p>
 *
 * @author humdeef
 */
@FunctionalInterface
public interface BizTraceIdGenerator {

    /**
     * 生成业务链路 ID。
     *
     * @return 链路 ID
     */
    String generate();
}
