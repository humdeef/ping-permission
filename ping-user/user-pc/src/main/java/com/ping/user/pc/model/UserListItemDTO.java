package com.ping.user.pc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import lombok.Data;

import java.util.List;

/**
 * UserListItemDTO - 用户列表项 DTO
 *
 * <p>角色、角色权限、接口资源按当前登录人的权限逐层决定是否返回：
 * 缺少对应权限时字段为 null，序列化时直接省略（{@link JsonInclude}）。</p>
 *
 * @author humdeef
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserListItemDTO {
    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 部门 ID */
    private Long deptId;
    /** 部门名称 */
    private String deptName;
    /** 状态 */
    private Integer status;

    /** 需要 system:role 权限 */
    private List<Long> roleIds;

    /** 需要 system:role 权限 */
    private List<String> roleNames;

    /** 该用户所有角色拥有的权限，需要 system:permission 权限 */
    private List<SysPermission> permissions;

    /** 上述权限挂接的接口资源，需要 system:apiresource 权限 */
    private List<SysApiResource> resources;
}
