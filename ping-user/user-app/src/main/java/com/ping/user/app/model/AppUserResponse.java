package com.ping.user.app.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AppUserResponse - App 用户响应数据。
 *
 * @author humdeef
 */
@Data
public class AppUserResponse {
    /** App 用户 ID（雪花 ID，字符串避免前端精度丢失） */
    private String userId;
    /** 手机号 */
    private String phone;
    /** 昵称 */
    private String nickname;
    /** 头像 */
    private String avatar;
    /** 性别：0未知 1男 2女 */
    private Integer gender;
    /** 状态 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
