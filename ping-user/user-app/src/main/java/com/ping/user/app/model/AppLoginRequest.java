package com.ping.user.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AppLoginRequest - App 登录请求参数。
 *
 * @author humdeef
 */
@Data
public class AppLoginRequest {
    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号长度不能超过32")
    private String phone;
    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;
}
