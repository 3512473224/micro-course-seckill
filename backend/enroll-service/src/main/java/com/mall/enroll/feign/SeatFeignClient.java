package com.mall.enroll.feign;

import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.dto.ReleaseSeatRequest;
import com.mall.common.dto.SeatDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * enroll-service -> seat-service：扣减/释放课程名额、查询名额。
 *
 * fallback = SeatFeignFallback：当 seat-service 被 Sentinel 熔断 / 宕机时，
 * 不会一直阻塞等待，而是快速走降级逻辑（抛业务异常，提示用户稍后重试）。
 * 前提：application.yml 里 feign.sentinel.enabled=true。
 */
@FeignClient(name = "seat-service", path = "/api/seat", fallback = SeatFeignFallback.class)
public interface SeatFeignClient {

    @PostMapping("/deduct")
    Result<Void> deduct(@RequestBody DeductSeatRequest request);

    /** 退课时释放名额：available 增加，上限为 total */
    @PostMapping("/release")
    Result<Void> release(@RequestBody ReleaseSeatRequest request);

    /** 名额详情：候补资格判断（是否已满）、退课补位时确认还有名额 */
    @GetMapping("/{courseId}")
    Result<SeatDTO> info(@PathVariable("courseId") Long courseId);
}
