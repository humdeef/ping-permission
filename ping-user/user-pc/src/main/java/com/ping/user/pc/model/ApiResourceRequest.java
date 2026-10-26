package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ApiResourceRequest - 接口资源新增/编辑请求
 *
 * @author humdeef
 */
@Data
public class ApiResourceRequest {
    /** 主键 ID */
    private Long id;
    /** 关联权限编码 */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 128, message = "权限编码长度不能超过128")
    private String permissionCode;
    /** 接口路径 */
    @NotBlank(message = "接口路径不能为空")
    @Size(max = 256, message = "接口路径长度不能超过256")
    private String path;
    /** 请求方法 */
    @Size(max = 16, message = "请求方法长度不能超过16")
    private String method = "*";
    /** 状态：0禁用 1启用 */
    private Integer status = 1;
    /** 描述 */
    @Size(max = 256, message = "描述长度不能超过256")
    private String description;
    /** 排序 */
    private Integer sort = 0;
}
