package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LoginRequest - 登录请求参数。
 *
 * @author humdeef
 */
@Data
public class LoginRequest {
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;
    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;

    /** 验证码 ID（开启验证码时必填） */
    private String captchaId;

    /** 验证码答案 */
    private String captchaCode;
}
