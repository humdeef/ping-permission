package com.ping.user.pc.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OnlineUserDTO - OnlineUserDTO。
 *
 * @author humdeef
 */
@Data
public class OnlineUserDTO {
    /** 登录 token */
    private String token;
    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** IP 地址 */
    private String ip;
    /** User-Agent */
    private String userAgent;
    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 剩余有效期（秒） */
    private Long timeoutSeconds;
}
