package com.mall.course.feign;

import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * course-service -> enroll-service：抢课成功后创建选课单。
 * name 必须和 enroll-service 的 spring.application.name 一致，走 Nacos 服务发现。
 */
@FeignClient(name = "enroll-service", path = "/api/enroll")
public interface EnrollFeignClient {

    @PostMapping("/seckill")
    Result<Long> createSeckillEnroll(@RequestBody SeckillEnrollRequest request);
}
