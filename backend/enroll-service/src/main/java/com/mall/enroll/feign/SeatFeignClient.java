package com.mall.enroll.feign;

import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * enroll-service -> seat-service：扣减课程剩余名额。
 *
 * fallback = SeatFeignFallback：当 seat-service 被 Sentinel 熔断 / 宕机时，
 * 不会一直阻塞等待，而是快速走降级逻辑（抛业务异常，提示用户稍后重试）。
 * 前提：application.yml 里 feign.sentinel.enabled=true。
 */
@FeignClient(name = "seat-service", path = "/api/seat", fallback = SeatFeignFallback.class)
public interface SeatFeignClient {

    @PostMapping("/deduct")
    Result<Void> deduct(@RequestBody DeductSeatRequest request);

    @GetMapping("/{courseId}")
    Result<Integer> remain(@PathVariable("courseId") Long courseId);
}
