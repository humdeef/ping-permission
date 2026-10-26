package com.ping.id;

import com.ping.core.id.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SnowflakeIdGenerator - 雪花算法分布式 ID 生成器。
 *
 * <p>64 位结构：</p>
 * <pre>
 * 0 | 41 bit 时间戳（相对 epoch） | 5 bit 数据中心 | 5 bit 工作节点 | 12 bit 序列号
 * </pre>
 *
 * <p>单节点每毫秒最多 4096 个 ID；时钟回拨超过 5ms 时直接抛异常，避免生成重复 ID。</p>
 *
 * @author humdeef
 */
public class SnowflakeIdGenerator implements DistributedIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    public static final long WORKER_BITS = 5L;
    public static final long DATACENTER_BITS = 5L;
    public static final long SEQUENCE_BITS = 12L;

    /** 最大工作节点 ID：31。 */
    public static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);

    /** 最大数据中心 ID：31。 */
    public static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS);

    /** 序列号掩码：4095。 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    private static final long MAX_BACKWARD_MS = 5L;

    private final long workerId;
    private final long datacenterId;
    private final long epoch;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long datacenterId, long epoch) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (epoch <= 0 || epoch > System.currentTimeMillis()) {
            throw new IllegalArgumentException("epoch must be a positive past timestamp");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
    }

    @Override
    public String nextId() {
        return Long.toString(nextLong());
    }

    /**
     * 生成 64 位 Long 型 ID。
     */
    public synchronized long nextLong() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_BACKWARD_MS) {
                throw new IllegalStateException(
                        "Clock moved backwards by " + offset + "ms, refusing to generate id");
            }
            log.warn("Clock moved backwards by {}ms, waiting for recovery", offset);
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                // 当前毫秒序列号用尽，等待下一毫秒
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long currentTime() {
        long now = System.currentTimeMillis();
        if (now < epoch) {
            throw new IllegalStateException("Current time is before the configured epoch");
        }
        return now;
    }

    private long waitUntil(long target) {
        long timestamp = currentTime();
        while (timestamp < target) {
            Thread.onSpinWait();
            timestamp = currentTime();
        }
        return timestamp;
    }
}
