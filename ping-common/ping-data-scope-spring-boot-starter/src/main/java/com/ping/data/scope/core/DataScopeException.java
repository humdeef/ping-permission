package com.ping.data.scope.core;

/**
 * 数据权限解析或注入失败。
 *
 * <p>该异常意味着查询不能安全执行，必须向上抛出，不能退化成全量查询。</p>
 *
 * @author humdeef
 */
public class DataScopeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataScopeException(String message) {
        super(message);
    }

    public DataScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
