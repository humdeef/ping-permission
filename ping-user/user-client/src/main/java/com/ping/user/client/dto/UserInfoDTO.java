package com.ping.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * UserInfo - 包含角色信息的用户模型，供网关和外部服务使用
 *
 * @author humdeef
 * @date 2026/10/19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private Integer age;
    private String address;
    private String gender;
    /** 用户角色列表 */
    private List<String> roles;
    /** 用户权限列表 */
    private List<String> permissions;
}