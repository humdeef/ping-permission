package com.ping.core.dto;

import lombok.Data;

/**
 * ApiResponse - 统一响应结构。
 *
 * <p>所有 REST 接口统一返回 {@code {code, message, data}}。</p>
 *
 *
 * @author humdeef
 * @param <T> 业务数据类型
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(400, message);
    }
}
