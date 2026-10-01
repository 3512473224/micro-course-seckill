package com.mall.course.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.dto.SeckillResult;

/**
 * Sentinel 限流后的兜底：方法必须是 public static，参数 = 原方法参数 + BlockException，返回值一致。
 * 注意：blockHandler 只处理"被 Sentinel 规则拦截"的情况；
 * 业务异常（名额不足、重复抢课）走正常异常流程，不会进这里。
 */
public class SeckillBlockHandler {

    public static SeckillResult seckillBlock(Long courseId, Long userId, BlockException ex) {
        return new SeckillResult(false, "抢课太火爆了，系统限流保护中，请稍后再试", null);
    }
}
