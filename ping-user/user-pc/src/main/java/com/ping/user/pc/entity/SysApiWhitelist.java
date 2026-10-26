package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysApiWhitelist - 接口白名单实体。
 *
 * @author humdeef
 */
@TableName("sys_api_whitelist")
@Data
public class SysApiWhitelist {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 接口路径 */
    private String path;
    /** 请求方法，* 表示任意 */
    private String method = "GET";
    /** 描述 */
    private String description;

    /** 状态：0禁用 1启用 */
    private Integer status = 1;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
