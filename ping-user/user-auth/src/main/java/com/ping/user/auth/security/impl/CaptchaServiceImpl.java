package com.ping.user.auth.security.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.auth.security.CaptchaService;

import com.ping.user.auth.config.PingSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CaptchaServiceImpl - 算术验证码。
 *
 * <p>验证码一次性使用，存放在 Redis 中并设置短 TTL。
 * 默认关闭，可通过 {@code ping.security.captcha.enabled=true} 开启。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {
    /** Redis 客户端 */
    private final StringRedisTemplate redisTemplate;
    /** 登录安全配置 */
    private final PingSecurityProperties properties;
    /** {@inheritDoc} 算术题验证码，答案存 Redis，一次性使用。 */
    @Override
    public Captcha create() {
        int left = ThreadLocalRandom.current().nextInt(1, 10);
        int right = ThreadLocalRandom.current().nextInt(1, 10);
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(UserConstants.CAPTCHA_KEY_PREFIX + id, String.valueOf(left + right),
                    Duration.ofMinutes(properties.getCaptcha().getExpireMinutes()));
        } catch (Exception e) {
            log.warn("[Captcha] 写入验证码失败: {}", e.getMessage());
        }
        return new Captcha(id, left + " + " + right + " = ?");
    }

    /**
     * 校验并删除验证码（一次性）。
     */
    @Override
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        String key = UserConstants.CAPTCHA_KEY_PREFIX + captchaId;
        try {
            String expected = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            return expected != null && expected.equals(captchaCode.trim());
        } catch (Exception e) {
            log.warn("[Captcha] 校验验证码失败: {}", e.getMessage());
            return false;
        }
    }
}
