package com.ping.user.pc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AuthConfigResponse - 登录页所需的公开配置。
 *
 * @author humdeef
 */
@Data
@AllArgsConstructor
public class AuthConfigResponse {
    /** 是否启用验证码 */
    private boolean captchaEnabled;
    /** 是否开放自助注册 */
    private boolean registrationEnabled;
}
