package com.mall.enroll.feign;

import com.mall.common.dto.IdListRequest;
import com.mall.common.dto.UserBriefDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * enroll-service -> user-service：批量查用户简要信息。
 * "我的选课单"需要展示授课教师姓名：course.teacherId -> user-service 批量换成 nickname，
 * 拿不到时回退用 course 表里的 teacher 快照字段。
 */
@FeignClient(name = "user-service", path = "/api/user")
public interface UserFeignClient {

    @PostMapping("/batch")
    Result<Map<Long, UserBriefDTO>> batch(@RequestBody IdListRequest request);
}
