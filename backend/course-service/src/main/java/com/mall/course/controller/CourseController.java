package com.mall.course.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.CoursePhaseDTO;
import com.mall.common.dto.IdListRequest;
import com.mall.common.result.Result;
import com.mall.course.dto.ReviewRequest;
import com.mall.course.dto.ReviewSummaryVO;
import com.mall.course.entity.Course;
import com.mall.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 课程查询/评价/阶段接口：读多写少，Service 层走了 Redis 缓存 */
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

    /**
     * 批量查课程：供 enroll-service 做时间冲突/学分校验用。
     * 为什么不用循环调 /{id}？N 次远程调用的 RTT 累加太慢，一次批量返回是常规优化。
     */
    @PostMapping("/batch")
    public Result<List<CourseDTO>> batch(@RequestBody IdListRequest request) {
        return Result.ok(courseService.batch(request.getIds()));
    }

    /** 当前进行中的选课阶段；无则 data 为 null，前端据此展示"不在选课阶段" */
    @GetMapping("/phase/current")
    public Result<CoursePhaseDTO> currentPhase() {
        return Result.ok(courseService.currentPhase());
    }

    @GetMapping("/phase/list")
    public Result<List<CoursePhaseDTO>> phaseList() {
        return Result.ok(courseService.listPhases());
    }

    /** 按 id 查阶段：志愿结算时 enroll-service 用它核验 phaseId 的合法性 */
    @GetMapping("/phase/{id}")
    public Result<CoursePhaseDTO> phaseById(@PathVariable Long id) {
        return Result.ok(courseService.getPhase(id));
    }

    /**
     * 发表课程评价：只有选上过该课的学生能评（Service 内调 enroll-service 核验）。
     * 网关角色校验只拦教师/管理路径，学生评价走这里，用 userId 即可。
     */
    @PostMapping("/review")
    public Result<Void> review(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                               @Valid @RequestBody ReviewRequest request) {
        courseService.addReview(userId, request.getCourseId(), request.getScore(), request.getComment());
        return Result.ok();
    }

    @GetMapping("/review/{courseId}")
    public Result<ReviewSummaryVO> reviewSummary(@PathVariable Long courseId) {
        return Result.ok(courseService.reviewSummary(courseId));
    }
}
