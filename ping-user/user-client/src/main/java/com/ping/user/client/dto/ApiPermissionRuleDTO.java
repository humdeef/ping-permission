package com.ping.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ApiPermissionRule - 接口权限规则
 *
 * <p>描述「请求方法 + 请求路径 -> 权限编码」的映射，数据来源于 sys_permission 表，
 * 供网关加载后对请求做统一鉴权。</p>
 *
 * @author humdeef
 * @date 2026/10/20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiPermissionRuleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 权限编码，如 system:user:add */
    private String code;
    /** 请求方法：GET/POST/PUT/DELETE，* 表示任意方法 */
    private String method;
    /** Ant 风格请求路径，如 /user/users/** */
    private String path;
}
