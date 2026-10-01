package com.mall.user;

import com.mall.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * 用户服务：登录签发 Token、个人信息查询。
 * 演示性质用内存 mock 用户（与 sql/init.sql 的 user 表数据保持一致）；
 * 生产环境这里接 user 表 + 密码加密（BCrypt）+ Redis 登录态。
 */
@EnableDiscoveryClient
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
