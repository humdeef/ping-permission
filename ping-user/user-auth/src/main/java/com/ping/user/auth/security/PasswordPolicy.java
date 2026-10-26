package com.ping.user.auth.security;

import com.ping.user.auth.config.PingSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PasswordPolicy - 密码复杂度校验。
 *
 * @author humdeef
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** 密码策略配置 */
    private final PingSecurityProperties properties;

    /**
     * 校验密码复杂度。
     *
     * @param password 明文密码
     * @throws RuntimeException 密码为空、长度不符或字符种类不足
     */
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("密码不能为空");
        }
        PingSecurityProperties.Password config = properties.getPassword();
        if (password.length() < config.getMinLength()) {
            throw new RuntimeException("密码长度不能少于 " + config.getMinLength() + " 位");
        }
        if (password.length() > config.getMaxLength()) {
            throw new RuntimeException("密码长度不能超过 " + config.getMaxLength() + " 位");
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isUpperCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            categories++;
        }
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            categories++;
        }
        if (categories < config.getRequireCategories()) {
            throw new RuntimeException("密码需包含大写字母、小写字母、数字、特殊字符中的至少 "
                    + config.getRequireCategories() + " 类");
        }
    }
}
