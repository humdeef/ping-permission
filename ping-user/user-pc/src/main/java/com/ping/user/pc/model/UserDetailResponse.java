package com.ping.user.pc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * UserDetailResponse - 用户详情响应。
 *
 * <p>角色、权限、接口资源按当前登录人的权限逐层开放；
 * 无权限查看的字段为 null，序列化时省略。</p>
 *
 * @author humdeef
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDetailResponse {
    /** 主键 ID */
    private Long id;
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
    /** 是否强制修改密码：0否 1是 */
    private Integer forceChangePassword;
    /** 密码最后修改时间 */
    private LocalDateTime passwordUpdatedAt;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 角色 ID 列表 */
    private Set<Long> roleIds;
    /** 角色名称列表 */
    private List<String> roleNames;
    /** 权限列表 */
    private List<SysPermission> permissions;
    /** 接口资源列表 */
    private List<SysApiResource> resources;
}
