package com.mall.seat.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.exception.BizException;

/**
 * 扣名额接口被 Sentinel 限流时的兜底。
 * blockHandler 要求：public static、返回值与原方法一致（void）、最后多一个 BlockException 参数。
 * 这里选择抛业务异常，让上游（enroll-service 的 @GlobalTransactional）感知失败并回滚，
 * 而不是吞掉异常返回"成功"——名额类操作绝不能"假成功"。
 */
public class SeatBlockHandler {

    public static void deductBlock(DeductSeatRequest request, BlockException ex) {
        throw new BizException("名额服务限流保护中，请稍后重试");
    }
}
