package com.mall.course.controller;

import com.mall.common.result.Result;
import com.mall.course.entity.Course;
import com.mall.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 课程查询接口：读多写少，Service 层走了 Redis 缓存 */
@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/list")
    public Result<List<Course>> list() {
        return Result.ok(courseService.list());
    }

    @GetMapping("/{id}")
    public Result<Course> getById(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course == null) {
            return Result.fail("课程不存在");
        }
        return Result.ok(course);
    }
}
