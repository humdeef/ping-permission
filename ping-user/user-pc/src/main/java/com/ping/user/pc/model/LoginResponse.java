package com.ping.user.pc.model;

import lombok.Data;

/**
 * LoginResponse - LoginResponse。
 *
 * @author humdeef
 */
@Data
public class LoginResponse {
    /** 登录 token */
    private String token;
    /** token 名称 */
    private String tokenName;

    /** 剩余有效期（秒） */
    private long tokenTimeout;

    /** 剩余活跃有效期（秒） */
    private long tokenActiveTimeout;

    /** 绝对过期时间（epoch 毫秒），永不过期为 null */
    private Long expiresAt;
    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;

    /** 是否需要强制修改密码 */
    private boolean forceChangePassword;
}
