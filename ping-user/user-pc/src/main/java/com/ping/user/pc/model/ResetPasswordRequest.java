package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ResetPasswordRequest - ResetPasswordRequest。
 *
 * @author humdeef
 */
@Data
public class ResetPasswordRequest {
    /** 密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(max = 64, message = "新密码长度不能超过64")
    private String password;
}
