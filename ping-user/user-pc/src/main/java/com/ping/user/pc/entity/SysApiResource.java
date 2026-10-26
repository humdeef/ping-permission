package com.ping.user.pc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysApiResource - 接口资源（网关鉴权规则）
 *
 * <p>与 {@link SysPermission} 解耦：权限表只负责「菜单/按钮」的权限定义（code），
 * 本表描述「接口路径 + 方法 → 权限编码」的鉴权映射，支持一个权限编码保护多个接口。</p>
 *
 * @author humdeef
 */
@TableName("sys_api_resource")
@Data
public class SysApiResource {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联权限编码（sys_permission.code），非外键，保存时校验存在性 */
    private String permissionCode;

    /** 接口请求路径（Ant 风格，如 /user/users/**） */
    private String path;

    /** 接口请求方法：GET/POST/PUT/DELETE，* 表示任意 */
    private String method = "*";

    /** 状态：0禁用 1启用 */
    private Integer status = 1;
    /** 描述 */
    private String description;
    /** 排序 */
    private Integer sort = 0;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
