package com.ping.user.pc.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CurrentUserResponse - CurrentUserResponse。
 *
 * @author humdeef
 */
@Data
public class CurrentUserResponse {
    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 状态 */
    private Integer status;
    /** 部门 ID */
    private Long deptId;
    /** 部门名称 */
    private String deptName;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 是否强制修改密码：0否 1是 */
    private boolean forceChangePassword;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
    /** 当前用户可见的菜单树（目录 + 菜单） */
    private List<MenuNode> menus;
    /** 剩余有效期（秒） */
    private long tokenTimeout;
    /** 剩余活跃有效期（秒） */
    private long tokenActiveTimeout;
    /** 绝对过期时间（epoch 毫秒） */
    private Long expiresAt;
}
