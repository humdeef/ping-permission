package com.ping.user.auth.security.impl;

import com.ping.user.auth.config.PingSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数与账号锁定测试。
 */
class LoginAttemptServiceImplTest {

    private static final String FAIL_KEY = "ping:security:login:fail:alice";
    private static final String LOCK_KEY = "ping:security:login:lock:alice";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private LoginAttemptServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        service = new LoginAttemptServiceImpl(redis, new PingSecurityProperties());
    }

    @Test
    @DisplayName("第一次失败返回剩余次数并设置过期时间")
    void firstFailureSetsExpire() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L);

        assertEquals(4, service.recordFailure("alice"));

        verify(redis).expire(FAIL_KEY, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("未达到上限时返回剩余可尝试次数")
    void belowLimitReturnsRemainingAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(3L);

        assertEquals(2, service.recordFailure("alice"));
    }

    @Test
    @DisplayName("达到上限时写入锁定标记")
    void reachingLimitWritesLockKey() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(5L);

        assertEquals(0, service.recordFailure("alice"));

        verify(valueOps).set(LOCK_KEY, "1", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("Redis 计数异常时降级为不锁定")
    void redisFailureDegradesToUnlocked() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertEquals(5, service.recordFailure("alice"));
    }

    @Test
    @DisplayName("增量返回 null 时按未锁定处理")
    void nullIncrementReturnsMaxAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(null);

        assertEquals(5, service.recordFailure("alice"));
    }

    @Test
    @DisplayName("锁定状态查询")
    void checksLockState() {
        when(redis.hasKey(LOCK_KEY)).thenReturn(true);
        assertTrue(service.isLocked("alice"));

        when(redis.hasKey(LOCK_KEY)).thenReturn(false);
        assertFalse(service.isLocked("alice"));

        when(redis.hasKey(LOCK_KEY)).thenThrow(new IllegalStateException("redis down"));
        assertFalse(service.isLocked("alice"));
    }

    @Test
    @DisplayName("剩余锁定时间：无效值返回 0")
    void returnsRemainingLockSeconds() {
        when(redis.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(120L);
        assertEquals(120, service.remainingLockSeconds("alice"));

        when(redis.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(-1L);
        assertEquals(0, service.remainingLockSeconds("alice"));

        when(redis.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(null);
        assertEquals(0, service.remainingLockSeconds("alice"));

        when(redis.getExpire(LOCK_KEY, TimeUnit.SECONDS))
                .thenThrow(new IllegalStateException("redis down"));
        assertEquals(0, service.remainingLockSeconds("alice"));
    }

    @Test
    @DisplayName("登录成功后同时清理失败计数和锁定标记")
    void clearRemovesBothKeys() {
        service.clear("alice");

        verify(redis).delete(FAIL_KEY);
        verify(redis).delete(LOCK_KEY);
    }
}
