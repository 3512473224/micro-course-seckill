package com.mall.course;

import com.mall.common.exception.GlobalExceptionHandler;
import com.mall.common.feign.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 课程服务：课程 CRUD（读多写少，Redis 缓存）+ 秒杀抢课入口（高并发）。
 * 生产建议：课程读服务与抢课写服务拆分，抢课单独扩容。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mall.course.feign")
@MapperScan("com.mall.course.mapper")
@SpringBootApplication
@Import({GlobalExceptionHandler.class, FeignConfig.class})
public class CourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
