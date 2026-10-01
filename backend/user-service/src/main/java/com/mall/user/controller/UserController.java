package com.mall.user.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.result.Result;
import com.mall.user.dto.LoginRequest;
import com.mall.user.dto.LoginResponse;
import com.mall.user.dto.UserVO;
import com.mall.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

/**
 * @RefreshScope：Nacos 配置中心改了 mall-common.yaml 后，
 * 调 /actuator/refresh 或 Nacos 推送即可刷新 @Value 字段，不用重启服务。
 * 这是"配置中心"最直观的演示：改 Nacos 页面 -> 刷新本接口 -> 文案变了。
 */
@RefreshScope
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Value("${mall.demo.banner:欢迎来到校园抢课系统}")
    private String banner;

    /** 登录：网关白名单接口，唯一不需要 Token 的入口 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request.getUsername(), request.getPassword()));
    }

    /** 个人信息：userId 由网关鉴权后写入 X-User-Id 透传，服务内不再解析 Token */
    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(userService.me(userId));
    }

    /** 配置中心动态刷新演示：去 Nacos 改 mall-common.yaml 的 mall.demo.banner 再调本接口 */
    @GetMapping("/config-demo")
    public Result<String> configDemo() {
        return Result.ok(banner);
    }
}
