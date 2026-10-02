package com.mall.enroll.feign;

import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.dto.ReleaseSeatRequest;
import com.mall.common.dto.SeatDTO;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * seat-service 的 Feign 降级：熔断/超时/服务不可用时触发。
 * 为什么降级里直接抛 BizException 而不是返回"成功"？
 * 降级绝不能"假装成功"——选课是资金/名额类操作，宁可明确失败让用户重试，
 * 也不能让用户以为选上了实际没扣名额（数据不一致更难收拾）。
 */
@Slf4j
@Component
public class SeatFeignFallback implements SeatFeignClient {

    @Override
    public Result<Void> deduct(DeductSeatRequest request) {
        log.error("seat-service 熔断降级：扣减名额失败 courseId={}", request.getCourseId());
        throw new BizException("名额服务繁忙（已触发熔断降级），请稍后重试");
    }

    @Override
    public Result<Void> release(ReleaseSeatRequest request) {
        log.error("seat-service 熔断降级：释放名额失败 courseId={}", request.getCourseId());
        throw new BizException("名额服务繁忙（已触发熔断降级），请稍后重试");
    }

    @Override
    public Result<SeatDTO> info(Long courseId) {
        log.error("seat-service 熔断降级：查询名额失败 courseId={}", courseId);
        throw new BizException("名额服务繁忙，请稍后重试");
    }
}
