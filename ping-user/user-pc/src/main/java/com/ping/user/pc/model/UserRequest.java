package com.ping.user.pc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * UserRequest - 用户请求参数。
 *
 * @author humdeef
 */
@Data
public class UserRequest {
    /** 主键 ID */
    private Long id;
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;
    /** 密码；编辑时为空表示不修改 */
    @Size(max = 256, message = "密码长度不能超过256")
    private String password;
    /** 昵称 */
    @Size(max = 64, message = "昵称长度不能超过64")
    private String nickname;
    /** 邮箱 */
    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;
    /** 手机号 */
    @Size(max = 32, message = "手机号长度不能超过32")
    private String phone;
    /** 状态：0禁用 1启用 */
    private Integer status = 1;
    /** 部门 ID；为空表示不修改 */
    private Long deptId;
    /** 角色 ID 列表 */
    private java.util.List<Long> roleIds;
}
