package com.ping.user.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PingSecurityProperties - 登录与密码安全配置。
 *
 * @author humdeef
 */
@Data
@ConfigurationProperties(prefix = "ping.security")
public class PingSecurityProperties {

    /** 是否开放自助注册 */
    private boolean registrationEnabled = true;

    /** 自助注册用户的默认角色编码（最小权限） */
    private String defaultRoleCode = "guest";

    private Login login = new Login();

    private Password password = new Password();

    private Captcha captcha = new Captcha();

    @Data
    public static class Login {
        /** 连续失败次数上限 */
        private int maxAttempts = 5;
        /** 锁定时长（分钟） */
        private int lockMinutes = 15;
    }

    @Data
    public static class Password {
        private int minLength = 8;
        private int maxLength = 64;
        /** 大写/小写/数字/特殊字符至少满足几类 */
        private int requireCategories = 3;
    }

    @Data
    public static class Captcha {
        /** 是否启用验证码（启用后登录必须携带） */
        private boolean enabled = false;
        /** 有效期（分钟） */
        private int expireMinutes = 2;
    }
}
