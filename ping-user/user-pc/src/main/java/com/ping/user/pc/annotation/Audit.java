package com.ping.user.pc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audit - 标记需要写审计日志的方法。
 *
 * @author humdeef
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /** 动作名称，如 新增用户 */
    String action();

    /** 目标类型，如 用户 */
    String targetType() default "";
}
