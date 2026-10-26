package com.ping.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Collections;
import java.util.Set;

/**
 * UserContextHolder - 当前登录用户上下文（跨线程可传递）。
 *
 * <p>使用 {@link TransmittableThreadLocal}：</p>
 * <ul>
 *     <li>普通子线程：自动继承（InheritableThreadLocal 语义）</li>
 *     <li>线程池 / @Async / CompletableFuture：任务需经过 {@code TtlRunnable}/{@code TtlExecutors}
 *         包装（ping-common 已提供 TaskDecorator，order-service 的异步线程池也已包装）</li>
 * </ul>
 *
 * @author humdeef
 */
public final class UserContextHolder {

    private static final TransmittableThreadLocal<UserContext> CONTEXT = new TransmittableThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        UserContext context = CONTEXT.get();
        return context == null ? null : context.getUserId();
    }

    /**
     * 当前操作人类型；未登录时返回 {@link OperatorType#ANONYMOUS}。
     */
    public static OperatorType getOperatorType() {
        UserContext context = CONTEXT.get();
        return context == null ? OperatorType.ANONYMOUS : context.getOperatorType();
    }

    /**
     * 当前用户展示名；旧 token 或未写入时可能为 {@code null}。
     */
    public static String getUserName() {
        UserContext context = CONTEXT.get();
        return context == null ? null : context.getUserName();
    }

    /**
     * 当前请求是否已登录（后台或 App）。
     *
     * <p>业务代码统一使用本方法判断登录，不需要区分 StpUtil / StpAppUtil。</p>
     */
    public static boolean isLogin() {
        return getUserId() != null;
    }

    /**
     * 当前用户是否 App 用户。
     *
     * <p>优先使用显式的 {@link OperatorType}；旧上下文没有类型时，
     * 由构造函数根据 {@code app} 角色推断，行为保持兼容。</p>
     */
    public static boolean isAppUser() {
        return getOperatorType() == OperatorType.APP;
    }

    /**
     * 当前用户是否后台用户。
     */
    public static boolean isPcUser() {
        return getOperatorType() == OperatorType.PC;
    }

    public static Set<String> getRoles() {
        UserContext context = CONTEXT.get();
        return context == null ? Collections.emptySet() : context.getRoles();
    }

    public static Set<String> getPermissions() {
        UserContext context = CONTEXT.get();
        return context == null ? Collections.emptySet() : context.getPermissions();
    }

    public static boolean hasRole(String role) {
        UserContext context = CONTEXT.get();
        return context != null && context.hasRole(role);
    }

    public static boolean hasPermission(String permission) {
        UserContext context = CONTEXT.get();
        return context != null && context.hasPermission(permission);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
