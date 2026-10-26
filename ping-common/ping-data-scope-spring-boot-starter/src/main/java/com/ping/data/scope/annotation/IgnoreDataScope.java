package com.ping.data.scope.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跳过数据权限过滤。
 *
 * <p>用于登录、权限解析、内部任务等不应该受当前用户数据范围影响的查询。
 * 应尽量少用；每一处豁免都意味着一条绕过行级权限的通道。</p>
 *
 * @author humdeef
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreDataScope {
}
