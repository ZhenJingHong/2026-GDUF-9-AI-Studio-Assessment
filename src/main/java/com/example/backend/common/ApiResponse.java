package com.example.backend.common;

/**
 * 统一响应结构，所有接口都返回这个格式。
 * <p>
 * 前端只需判断 code 是否为 0：
 * <pre>
 * { "code": 0, "message": "ok", "data": ... }
 * </pre>
 * 约定见 docs/接口文档.md。
 *
 * @param code    业务状态码，0 表示成功
 * @param message 提示信息，成功时为 "ok"，失败时为原因
 * @param data    业务数据，失败时为 null
 */
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功响应，data 为业务数据 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /** 失败响应，data 固定为 null */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}