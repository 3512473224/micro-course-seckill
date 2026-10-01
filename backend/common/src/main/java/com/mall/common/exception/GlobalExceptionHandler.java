package com.mall.common.exception;

import com.mall.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。各服务在启动类上 @Import(GlobalExceptionHandler.class) 显式引入，
 * 而不是包扫描——避免 gateway（无 spring-web）误加载。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接把 message 透给前端展示 */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 未知异常：打日志 + 模糊提示，不把堆栈/内部细节暴露出去 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
