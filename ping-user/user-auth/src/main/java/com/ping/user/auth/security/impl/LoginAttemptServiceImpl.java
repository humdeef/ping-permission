package com.ping.user.auth.security.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.auth.security.LoginAttemptService;

import com.ping.user.auth.config.PingSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * LoginAttemptServiceImpl - 登录失败计数与账号锁定。
 *
 * <p>Redis 不可用时降级为「不锁定」，避免整个登录入口不可用；正常场景由
 * {@link PingSecurityProperties.Login} 控制阈值与锁定时长。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {
    /** Redis 客户端 */
    private final StringRedisTemplate redisTemplate;
    /** 登录安全配置 */
    private final PingSecurityProperties properties;
    /** {@inheritDoc} */
    @Override
    public boolean isLocked(String username) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(username)));
        } catch (Exception e) {
            log.warn("[LoginAttempt] 查询锁定状态失败 username={}: {}", username, e.getMessage());
            return false;
        }
    }
    /** {@inheritDoc} */
    @Override
    public long remainingLockSeconds(String username) {
        try {
            Long seconds = redisTemplate.getExpire(lockKey(username), java.util.concurrent.TimeUnit.SECONDS);
            return seconds == null || seconds < 0 ? 0 : seconds;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 记录一次失败，返回剩余可尝试次数；达到上限时同时写入锁定标记。
     */
    @Override
    public int recordFailure(String username) {
        int maxAttempts = properties.getLogin().getMaxAttempts();
        try {
            String key = failKey(username);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return maxAttempts;
            }
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(properties.getLogin().getLockMinutes()));
            }
            if (count >= maxAttempts) {
                redisTemplate.opsForValue().set(lockKey(username), "1",
                        Duration.ofMinutes(properties.getLogin().getLockMinutes()));
                return 0;
            }
            return (int) (maxAttempts - count);
        } catch (Exception e) {
            log.warn("[LoginAttempt] 记录失败次数异常 username={}: {}", username, e.getMessage());
            return maxAttempts;
        }
    }
    /** {@inheritDoc} */
    @Override
    public void clear(String username) {
        try {
            redisTemplate.delete(failKey(username));
            redisTemplate.delete(lockKey(username));
        } catch (Exception e) {
            log.warn("[LoginAttempt] 清理失败次数异常 username={}: {}", username, e.getMessage());
        }
    }

    private String failKey(String username) {
        return UserConstants.LOGIN_FAIL_KEY_PREFIX + username;
    }

    private String lockKey(String username) {
        return UserConstants.LOGIN_LOCK_KEY_PREFIX + username;
    }
}
