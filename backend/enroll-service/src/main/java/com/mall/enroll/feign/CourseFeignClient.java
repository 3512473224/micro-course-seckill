package com.mall.enroll.feign;

import com.mall.common.dto.CourseDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** enroll-service -> course-service：创建选课单时校验课程是否存在/是否上架 */
@FeignClient(name = "course-service", path = "/api/course")
public interface CourseFeignClient {

    @GetMapping("/{id}")
    Result<CourseDTO> getById(@PathVariable("id") Long id);
}
