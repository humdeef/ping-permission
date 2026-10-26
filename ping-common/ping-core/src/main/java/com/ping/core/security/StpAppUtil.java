package com.ping.core.security;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

/**
 * StpAppUtil - C 端独立登录域（loginType = app）。
 *
 * <p>与后台的 {@code StpUtil} 完全隔离：App token 不能访问后台接口，
 * 后台 token 也不能访问 App 接口。Redis key 由 Sa-Token 按 loginType 自动隔离。</p>
 *
 * <p>网关和服务端共用本类，保证两端的 loginType / token 读取方式一致。</p>
 *
 * @author humdeef
 */
public final class StpAppUtil {

    public static final String TYPE = "app";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    private StpAppUtil() {
    }

    public static void login(Object userId) {
        LOGIC.login(userId);
    }

    public static boolean isLogin() {
        return LOGIC.isLogin();
    }

    public static void checkLogin() {
        LOGIC.checkLogin();
    }

    public static Object getLoginId() {
        return LOGIC.getLoginId();
    }

    public static String getTokenValue() {
        return LOGIC.getTokenValue();
    }

    public static SaTokenInfo getTokenInfo() {
        return LOGIC.getTokenInfo();
    }

    public static void logout() {
        LOGIC.logout();
    }

    public static void logout(Object userId) {
        LOGIC.logout(userId);
    }
}
