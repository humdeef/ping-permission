package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RegisterRequest - 注册请求参数。
 *
 * @author humdeef
 */
@Data
public class RegisterRequest {
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;
    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;
    /** 昵称 */
    @Size(max = 64, message = "昵称长度不能超过64")
    private String nickname;
    /** 邮箱 */
    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;
    /** 手机号 */
    @Size(max = 32, message = "手机号长度不能超过32")
    private String phone;
}
