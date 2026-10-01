package com.mall.enroll;

import com.mall.common.exception.GlobalExceptionHandler;
import com.mall.common.feign.FeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 选课服务：分布式事务的发起方（@GlobalTransactional）。
 * 注意：必须排除 Seata 的数据源自动代理冲突？不需要——
 * spring-cloud-starter-alibaba-seata 会自动把 DataSource 包装成 DataSourceProxy，
 * MyBatis-Plus 经由代理执行 SQL，Seata 才能记录 undo_log 做回滚。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mall.enroll.feign")
@MapperScan("com.mall.enroll.mapper")
@SpringBootApplication
@Import({GlobalExceptionHandler.class, FeignConfig.class})
public class EnrollApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnrollApplication.class, args);
    }
}
