package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RoleRequest - 角色请求参数。
 *
 * @author humdeef
 */
@Data
public class RoleRequest {
    /** 主键 ID */
    private Long id;
    /** 角色编码（创建后不可修改） */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过64")
    private String code;
    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过64")
    private String name;
    /** 排序 */
    private Integer sort = 0;
    /** 备注 */
    @Size(max = 256, message = "备注长度不能超过256")
    private String remark;
    /** 数据范围：1全部 2本部门 3本部门及下级 4仅本人 5自定义；null 表示不修改 */
    private Integer dataScope;
    /** 自定义数据范围部门（dataScope=5 时生效） */
    private java.util.List<Long> deptIds;
    /** 权限 ID 列表 */
    private java.util.List<Long> permissionIds;
}
