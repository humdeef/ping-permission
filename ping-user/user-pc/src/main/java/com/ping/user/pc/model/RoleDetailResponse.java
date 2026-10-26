package com.ping.user.pc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ping.user.pc.entity.SysRole;
import lombok.Data;

import java.util.Set;

/**
 * RoleDetailResponse - 角色详情响应。
 *
 * @author humdeef
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDetailResponse {
    /** 角色信息 */
    private SysRole role;

    /** 角色拥有的权限 ID，无权限查看时为 null */
    private Set<Long> permissionIds;

    /** 自定义数据范围部门 ID */
    private Set<Long> deptIds;
}
