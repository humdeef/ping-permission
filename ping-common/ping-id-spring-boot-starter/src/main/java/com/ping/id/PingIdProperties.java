package com.ping.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PingIdProperties - 分布式 ID 配置。
 *
 * <pre>
 * ping:
 *   id:
 *     worker-id: 1       # 0-31，-1 表示按主机名自动推导
 *     datacenter-id: 0   # 0-31，-1 表示默认 0
 *     epoch: 1704067200000  # 起始时间戳，默认 2024-01-01T00:00:00Z
 * </pre>
 *
 * @author humdeef
 */
@ConfigurationProperties(prefix = "ping.id")
public class PingIdProperties {

    /** 工作节点 ID（0-31），-1 表示自动推导 */
    private long workerId = -1L;

    /** 数据中心 ID（0-31），-1 表示默认 0 */
    private long datacenterId = -1L;

    /** 起始时间戳（毫秒），默认 2024-01-01T00:00:00Z */
    private long epoch = 1704067200000L;

    public long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(long workerId) {
        this.workerId = workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }
}
