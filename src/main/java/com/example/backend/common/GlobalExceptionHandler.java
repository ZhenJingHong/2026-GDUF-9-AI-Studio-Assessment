package com.example.backend.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一兜底，防止程序因为越界等原因直接崩溃返回500白屏
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 捕获所有数组/集合越界异常
    @ExceptionHandler(IndexOutOfBoundsException.class)
    public ApiResponse<?> handleIndexOutOfBounds(IndexOutOfBoundsException e) {
        e.printStackTrace(); // 在控制台打印一下
        return ApiResponse.fail(40001, "请求参数越界啦，请检查索引或分页参数！");
    }

    // 2. 捕获非法参数异常 (比如把字母传给了数字参数)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException e) {
        e.printStackTrace();
        return ApiResponse.fail(40002, "参数不合法：" + e.getMessage());
    }

    // 3. 捕获所有其他未知异常
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        e.printStackTrace();
        return ApiResponse.fail(50000, "服务器开小差了，请联系管理员");
    }
}
