package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DeptRequest - DeptRequest。
 *
 * @author humdeef
 */
@Data
public class DeptRequest {
    /** 主键 ID */
    private Long id;

    /** 父部门 ID，0 表示根 */
    private Long parentId = 0L;
    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过64")
    private String name;
    /** 部门编码 */
    @Size(max = 64, message = "部门编码长度不能超过64")
    private String code;
    /** 负责人 */
    @Size(max = 64, message = "负责人长度不能超过64")
    private String leader;
    /** 联系电话 */
    @Size(max = 32, message = "联系电话长度不能超过32")
    private String phone;
    /** 邮箱 */
    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;
    /** 排序 */
    private Integer sort = 0;
    /** 状态：0禁用 1启用 */
    private Integer status = 1;
}
