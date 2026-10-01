package com.mall.user.service;

import com.mall.common.exception.BizException;
import com.mall.common.util.JwtUtil;
import com.mall.user.dto.LoginResponse;
import com.mall.user.dto.UserVO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用内存用户（与 sql/init.sql 里 user 表的测试账号保持一致，方便对照）。
 * 生产：查 user 表，密码用 BCrypt 校验，登录成功把 token jti 写入 Redis 做主动失效。
 */
@Service
public class UserService {

    /** username -> {id, password, nickname} */
    private static final Map<String, String[]> USERS = new ConcurrentHashMap<>();

    static {
        USERS.put("student01", new String[]{"1", "123456", "张同学"});
        USERS.put("student02", new String[]{"2", "123456", "李同学"});
        USERS.put("admin", new String[]{"3", "admin123", "教务管理员"});
    }

    public LoginResponse login(String username, String password) {
        String[] user = USERS.get(username);
        if (user == null || !user[1].equals(password)) {
            throw new BizException("用户名或密码错误");
        }
        Long userId = Long.parseLong(user[0]);
        String token = JwtUtil.generate(userId, username);
        return new LoginResponse(token, userId, username, user[2]);
    }

    public UserVO me(Long userId) {
        return USERS.entrySet().stream()
                .filter(e -> e.getValue()[0].equals(String.valueOf(userId)))
                .map(e -> new UserVO(userId, e.getKey(), e.getValue()[2]))
                .findFirst()
                .orElseThrow(() -> new BizException(401, "用户不存在"));
    }
}
