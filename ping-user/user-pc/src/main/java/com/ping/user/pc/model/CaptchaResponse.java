package com.ping.user.pc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CaptchaResponse - 验证码。
 *
 * @author humdeef
 */
@Data
@AllArgsConstructor
public class CaptchaResponse {
    /** 验证码 ID */
    private String captchaId;

    /** 算术题，例如 "3 + 5 = ?" */
    private String question;
}
