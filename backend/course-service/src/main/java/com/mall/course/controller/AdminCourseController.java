package com.mall.course.controller;

import com.mall.common.dto.CoursePhaseDTO;
import com.mall.common.result.Result;
import com.mall.course.dto.CreateCourseRequest;
import com.mall.course.dto.DashboardItemVO;
import com.mall.course.dto.PhaseRequest;
import com.mall.course.dto.UpdateCourseRequest;
import com.mall.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端：课程发布/改课/上下架、选课阶段管理、数据看板。
 * 路径以 /api/course/admin 开头，网关已强制要求 admin 角色，这里不再重复验。
 */
@RestController
@RequestMapping("/api/course/admin")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseService courseService;

    /** 管理员开课：直接发布（status=1），无需再走发布流程 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreateCourseRequest request) {
        return Result.ok(courseService.createCourse(null, "", request, 1));
    }

    /** 改课：时间/教室/容量/上下架 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody UpdateCourseRequest request) {
        courseService.updateCourse(id, request);
        return Result.ok();
    }

    /** 发布：草稿 -> 上架 */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        courseService.setStatus(id, 1);
        return Result.ok();
    }

    /** 下架：学生端列表不再展示，已选的学生不受影响 */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        courseService.setStatus(id, 0);
        return Result.ok();
    }

    /** 数据看板：[{courseId,name,total,enrolled,rate,avgScore}]，按已选人数倒序 */
    @GetMapping("/dashboard")
    public Result<List<DashboardItemVO>> dashboard() {
        return Result.ok(courseService.dashboard());
    }

    // ---------------- 选课阶段管理 ----------------

    @GetMapping("/phase")
    public Result<List<CoursePhaseDTO>> phases() {
        return Result.ok(courseService.listPhases());
    }

    @PostMapping("/phase")
    public Result<Long> createPhase(@Valid @RequestBody PhaseRequest request) {
        return Result.ok(courseService.createPhase(request));
    }

    @PutMapping("/phase/{id}")
    public Result<Void> updatePhase(@PathVariable Long id,
                                    @Valid @RequestBody PhaseRequest request) {
        courseService.updatePhase(id, request);
        return Result.ok();
    }

    /** 开关：0=关闭 <-> 1=进行中 */
    @PostMapping("/phase/{id}/toggle")
    public Result<Integer> togglePhase(@PathVariable Long id) {
        return Result.ok(courseService.togglePhase(id));
    }

    /** 志愿结算完成后由 enroll-service 调用：阶段置为已结束 */
    @PostMapping("/phase/{id}/finish")
    public Result<Void> finishPhase(@PathVariable Long id) {
        courseService.finishPhase(id);
        return Result.ok();
    }
}
