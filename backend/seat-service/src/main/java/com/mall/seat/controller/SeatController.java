package com.mall.seat.controller;

import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.result.Result;
import com.mall.seat.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 名额接口：主要给 enroll-service 经 Feign 内部调用；
 * 网关同样做了路由，方便演示时直接 curl 调试（生产可收敛为内网接口）。
 */
@RestController
@RequestMapping("/api/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/deduct")
    public Result<Void> deduct(@Valid @RequestBody DeductSeatRequest request) {
        seatService.deduct(request);
        return Result.ok();
    }

    @GetMapping("/{courseId}")
    public Result<Integer> remain(@PathVariable Long courseId) {
        return Result.ok(seatService.remain(courseId));
    }
}
