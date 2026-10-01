package com.mall.common.exception;

import lombok.Getter;

/**
 * 业务异常：service 层用它表达"可预期的失败"（名额不足、重复抢课、课程不存在…）。
 * 由 GlobalExceptionHandler 统一转成 Result，避免每个 controller 写 try-catch。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
