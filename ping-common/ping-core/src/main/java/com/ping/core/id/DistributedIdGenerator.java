package com.ping.core.id;

/**
 * DistributedIdGenerator - 分布式 ID 生成器 SPI。
 *
 * <p>由 ping-id starter 提供具体实现（内置雪花算法）；
 * 业务侧可通过提供自己的 Bean 覆盖。</p>
 *
 * @author humdeef
 */
@FunctionalInterface
public interface DistributedIdGenerator {

    /**
     * 生成下一个分布式 ID。
     *
     * @return 全局唯一 ID
     */
    String nextId();
}
