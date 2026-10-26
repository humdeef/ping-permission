package com.ping.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PublicApi - 公开接口：无需登录、无需 ping-token，直接访问。
 *
 * <p>可标注在方法或 Controller 类上。服务启动时会自动扫描并注册到网关，
 * 无需在网关配置文件或白名单表中逐条维护。</p>
 *
 * @author humdeef
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
