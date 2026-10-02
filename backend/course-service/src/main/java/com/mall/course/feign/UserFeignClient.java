package com.mall.course.feign;

import com.mall.common.dto.IdListRequest;
import com.mall.common.dto.UserBriefDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * course-service -> user-service：批量查用户简要信息。
 * 教师端花名册需要把选课学生的 id 换成"学号/姓名"，一次批量查完，避免 N+1 远程调用。
 */
@FeignClient(name = "user-service", path = "/api/user")
public interface UserFeignClient {

    @PostMapping("/batch")
    Result<Map<Long, UserBriefDTO>> batch(@RequestBody IdListRequest request);
}
