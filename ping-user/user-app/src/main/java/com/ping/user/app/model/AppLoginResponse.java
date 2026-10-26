package com.ping.user.app.model;

import lombok.Data;

/**
 * AppLoginResponse - App 登录响应数据。
 *
 * @author humdeef
 */
@Data
public class AppLoginResponse {
    /** 登录 token */
    private String token;
    /** token 名称 */
    private String tokenName;
    /** 剩余有效期（秒） */
    private long tokenTimeout;
    /** 剩余活跃有效期（秒） */
    private long tokenActiveTimeout;
    /** 绝对过期时间（epoch 毫秒） */
    private Long expiresAt;
    /** App 用户 ID（雪花 ID，字符串避免前端精度丢失） */
    private String userId;
    /** 手机号 */
    private String phone;
    /** 昵称 */
    private String nickname;
}
