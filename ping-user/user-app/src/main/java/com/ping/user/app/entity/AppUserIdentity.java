package com.ping.user.app.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AppUserIdentity - C 端用户第三方身份（手机号/微信/Apple 等）。
 *
 * @author humdeef
 */
@TableName("app_user_identity")
@Data
public class AppUserIdentity {
    /** 主键 ID（雪花 ID，应用层生成） */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 用户 ID */
    private Long userId;

    /** phone/wechat/apple/alipay */
    private String provider;

    /** 手机号 / OpenID / UnionID */
    private String identifier;
    /** 第三方凭证 */
    private String credential;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
