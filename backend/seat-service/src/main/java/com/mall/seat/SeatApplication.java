package com.mall.seat;

import com.mall.common.exception.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * 名额服务：Seata 分布式事务的分支方（被 @GlobalTransactional 纳管）。
 * 哪怕 enroll-service 挂了，只要全局事务没提交，TC 也会驱动本服务回滚。
 */
@EnableDiscoveryClient
@MapperScan("com.mall.seat.mapper")
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class SeatApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatApplication.class, args);
    }
}
