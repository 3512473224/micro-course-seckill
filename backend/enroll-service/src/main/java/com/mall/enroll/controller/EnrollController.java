package com.mall.enroll.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.result.Result;
import com.mall.enroll.dto.CreateEnrollRequest;
import com.mall.enroll.entity.EnrollOrder;
import com.mall.enroll.service.EnrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 选课接口 */
@RestController
@RequestMapping("/api/enroll")
@RequiredArgsConstructor
public class EnrollController {

    private final EnrollService enrollService;

    /** 普通选课：Seata AT 分布式事务（本地建单 + 远程扣名额） */
    @PostMapping("/create")
    public Result<Long> create(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                              @Valid @RequestBody CreateEnrollRequest request) {
        return Result.ok(enrollService.createEnroll(userId, request));
    }

    /**
     * 对比接口：无 Seata。演示用——传一个名额不足的 courseId，
     * 会看到选课单残留（脏数据），而 /create 接口同样场景下选课单会被回滚。
     */
    @PostMapping("/create-no-seata")
    public Result<Long> createNoSeata(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                                     @Valid @RequestBody CreateEnrollRequest request) {
        return Result.ok(enrollService.createEnrollNoSeata(userId, request));
    }

    /** 供 course-service 秒杀链路内部调用：userId 由请求体携带（服务间调用） */
    @PostMapping("/seckill")
    public Result<Long> seckill(@RequestBody SeckillEnrollRequest request) {
        return Result.ok(enrollService.createSeckillEnroll(request));
    }

    /** 我的课程表 */
    @GetMapping("/my")
    public Result<List<EnrollOrder>> my(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(enrollService.myOrders(userId));
    }
}
