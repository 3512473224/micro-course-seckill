package com.mall.user.service;

import com.mall.common.dto.UserBriefDTO;
import com.mall.common.exception.BizException;
import com.mall.common.util.JwtUtil;
import com.mall.user.dto.LoginResponse;
import com.mall.user.dto.UserVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用内存用户（与 sql/init.sql 里 user 表的测试账号保持一致，方便对照）。
 * 角色：student=学生，teacher=教师，admin=管理员；网关按角色做路径级鉴权。
 * 生产：查 user 表，密码用 BCrypt 校验，登录成功把 token jti 写入 Redis 做主动失效。
 */
@Service
public class UserService {

    /** username -> {id, password, nickname, role} */
    private static final Map<String, String[]> USERS = new ConcurrentHashMap<>();

    static {
        USERS.put("student01", new String[]{"1", "123456", "张同学", "student"});
        USERS.put("student02", new String[]{"2", "123456", "李同学", "student"});
        USERS.put("teacher01", new String[]{"4", "123456", "王教授", "teacher"});
        USERS.put("admin", new String[]{"3", "123456", "教务管理员", "admin"});
    }

    public LoginResponse login(String username, String password) {
        String[] user = USERS.get(username);
        if (user == null || !user[1].equals(password)) {
            throw new BizException("用户名或密码错误");
        }
        Long userId = Long.parseLong(user[0]);
        // token 里携带角色，网关验签后直接做角色校验，不用再调 user-service
        String token = JwtUtil.generate(userId, username, user[3]);
        return new LoginResponse(token, userId, username, user[2], user[3]);
    }

    public UserVO me(Long userId) {
        return USERS.entrySet().stream()
                .filter(e -> e.getValue()[0].equals(String.valueOf(userId)))
                .map(e -> new UserVO(userId, e.getKey(), e.getValue()[2], e.getValue()[3]))
                .findFirst()
                .orElseThrow(() -> new BizException(401, "用户不存在"));
    }

    /**
     * 批量查用户简要信息：给教师端花名册用，一次把选课学生 id 列表换成姓名。
     * 返回 {id:{username,nickname}}，查不到的 id 直接跳过。
     */
    public Map<Long, UserBriefDTO> batch(List<Long> ids) {
        Map<Long, UserBriefDTO> result = new HashMap<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            USERS.entrySet().stream()
                    .filter(e -> e.getValue()[0].equals(String.valueOf(id)))
                    .findFirst()
                    .ifPresent(e -> result.put(id,
                            new UserBriefDTO(e.getKey(), e.getValue()[2])));
        }
        return result;
    }
}
