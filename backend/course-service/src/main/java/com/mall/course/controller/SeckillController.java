package com.mall.course.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.dto.SeckillResult;
import com.mall.common.result.Result;
import com.mall.course.entity.SeckillCourse;
import com.mall.course.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 秒杀抢课接口 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** 首页"抢课专区"：当前可抢的活动列表（含开抢/结束时间，前端做倒计时） */
    @GetMapping("/active")
    public Result<List<SeckillCourse>> active() {
        return Result.ok(seckillService.activeList());
    }

    /** 抢课：高并发入口。userId 由网关写入 X-User-Id */
    @PostMapping("/{courseId}")
    public Result<SeckillResult> seckill(@PathVariable Long courseId,
                                        @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(seckillService.doSeckill(courseId, userId));
    }

    /** 剩余名额：前端轮询展示"仅剩 N 个名额" */
    @GetMapping("/stock/{courseId}")
    public Result<Long> stock(@PathVariable Long courseId) {
        return Result.ok(seckillService.remainStock(courseId));
    }

    /**
     * 名额预热：开抢前把 DB 名额数加载到 Redis。
     * 演示用直接调这个接口；生产由 XXL-Job/定时任务在开抢前 5 分钟执行。
     */
    @PostMapping("/init/{courseId}")
    public Result<String> init(@PathVariable Long courseId) {
        seckillService.initStock(courseId);
        return Result.ok("预热完成");
    }
}
