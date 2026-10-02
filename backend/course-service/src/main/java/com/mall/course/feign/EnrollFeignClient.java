package com.mall.course.feign;

import com.mall.common.dto.EnrolledStudentDTO;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * course-service -> enroll-service：
 *  - 抢课成功后创建选课单；
 *  - 课程评价前核验"该生是否选上过该课"；
 *  - 教师端花名册取选课学生 id 列表；
 *  - 管理端看板取各课程选上人数。
 * name 必须和 enroll-service 的 spring.application.name 一致，走 Nacos 服务发现。
 */
@FeignClient(name = "enroll-service", path = "/api/enroll")
public interface EnrollFeignClient {

    @PostMapping("/seckill")
    Result<Long> createSeckillEnroll(@RequestBody SeckillEnrollRequest request);

    /** 该生是否有 status=1 的选课单（课程评价资格核验用） */
    @GetMapping("/check")
    Result<Boolean> check(@RequestParam("studentId") Long studentId,
                          @RequestParam("courseId") Long courseId);

    /** 某课程已选上的学生 id 列表（花名册用） */
    @GetMapping("/course/{courseId}/students")
    Result<List<EnrolledStudentDTO>> courseStudents(@PathVariable("courseId") Long courseId);

    /** 各课程选上人数 {courseId: count}（管理端看板用） */
    @GetMapping("/admin/counts")
    Result<Map<Long, Integer>> counts();
}
