package com.ping.user.pc.audit;

/**
 * AuditContext - 在业务方法内附加审计详情（如修改前/后的字段差异）。
 *
 * <p>由 {@code AuditInterceptor} 在请求结束时读取并写入审计日志，
 * 读取后立即清理，避免线程复用导致详情串到下一条请求。</p>
 *
 * @author humdeef
 */
public final class AuditContext {

    private static final ThreadLocal<String> DETAIL = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setDetail(String detail) {
        DETAIL.set(detail);
    }

    public static String getDetailAndClear() {
        String detail = DETAIL.get();
        DETAIL.remove();
        return detail;
    }
}
