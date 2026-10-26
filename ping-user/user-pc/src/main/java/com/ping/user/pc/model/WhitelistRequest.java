package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * WhitelistRequest - 白名单请求参数。
 *
 * @author humdeef
 */
@Data
public class WhitelistRequest {
    /** 主键 ID */
    private Long id;
    /** 接口路径 */
    @NotBlank(message = "接口路径不能为空")
    @Size(max = 256, message = "接口路径长度不能超过256")
    private String path;
    /** 请求方法 */
    @NotBlank(message = "请求方法不能为空")
    @Size(max = 16, message = "请求方法长度不能超过16")
    private String method = "GET";
    /** 描述 */
    @Size(max = 256, message = "描述长度不能超过256")
    private String description;
    /** 状态：0禁用 1启用 */
    private Integer status = 1;
}
