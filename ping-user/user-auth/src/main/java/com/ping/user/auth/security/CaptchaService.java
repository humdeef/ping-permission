package com.ping.user.auth.security;

/**
 * 算术验证码。
 *
 * @author humdeef
 */
public interface CaptchaService {

    /**
     * 生成一次性验证码。
     * @return 验证码
     */
    Captcha create();

    /**
     * 校验并删除验证码（一次性）。
     *
     * @param captchaId 验证码 ID
     * @param captchaCode 验证码答案
     * @return 是否满足条件
     */
    boolean verify(String captchaId, String captchaCode);

    /**
     * 验证码内容。
     *
     * @param captchaId 验证码 ID
     * @param question  算术题，例如 "3 + 5 = ?"
     */
    record Captcha(String captchaId, String question) {
    }
}
