package com.ping.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * LoginOnlyApi - 仅要求登录的接口（有效 ping-token），不校验具体权限。
 * 确定是后台登录的接口
 * <p>用于 PC 端「登录即可访问」的接口
 * （如退出登录、修改密码、当前用户信息）。可标注在方法或 Controller 类上，
 * 服务启动时自动注册到网关，无需维护网关配置或权限表。</p>
 *
 * @author humdeef
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginOnlyApi {
}
