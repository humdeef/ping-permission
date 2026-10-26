package com.ping.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AppLoginOnlyApi - C 端接口：只要求 App 登录态（StpLogic("app")），不校验后台权限。
 * 确定是移动端接口
 * <p>与后台的 {@link LoginOnlyApi} 区分：后台接口用 StpUtil，App 接口用 StpAppUtil，
 * 两套 token 域互不通用。网关根据本注解决定使用哪个登录域，不依赖 URL 前缀。</p>
 *
 * @author humdeef
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AppLoginOnlyApi {
}
