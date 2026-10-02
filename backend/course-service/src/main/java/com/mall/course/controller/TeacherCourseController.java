package com.mall.course.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.result.Result;
import com.mall.course.dto.CreateCourseRequest;
import com.mall.course.dto.RosterItemVO;
import com.mall.course.entity.Course;
import com.mall.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 教师端：开课（草稿）/我的课程/花名册/花名册导出。
 * 网关已按路径前缀做角色校验（teacher 或 admin 可进），服务内再验"课程归属"。
 */
@RestController
@RequestMapping("/api/course/teacher")
@RequiredArgsConstructor
public class TeacherCourseController {

    private final CourseService courseService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 开课：教师开的是草稿（status=0），需管理员发布后学生才能看到。
     * 为什么默认草稿？教师填错时间/教室时不会直接污染学生端列表，发布前可反复修改。
     */
    @PostMapping
    public Result<Long> create(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                              @Valid @RequestBody CreateCourseRequest request) {
        return Result.ok(courseService.createCourse(userId, "", request, 0));
    }

    /** 我的课程：teacherId=当前用户 */
    @GetMapping("/my")
    public Result<List<Course>> my(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(courseService.myCourses(userId));
    }

    /** 花名册：[{userId,username,nickname,orderNo,createdAt}] */
    @GetMapping("/{id}/roster")
    public Result<List<RosterItemVO>> roster(@PathVariable Long id,
                                            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(courseService.roster(id, userId));
    }

    /** 花名册导出：text/csv 下载，表头 学号,姓名,选课单号,选课时间 */
    @GetMapping("/{id}/roster/export")
    public ResponseEntity<byte[]> exportRoster(@PathVariable Long id,
                                               @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        List<RosterItemVO> roster = courseService.roster(id, userId);
        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM：Excel 打开中文不乱码
        csv.append("学号,姓名,选课单号,选课时间\n");
        for (RosterItemVO r : roster) {
            csv.append(escape(r.getUsername())).append(',')
                    .append(escape(r.getNickname())).append(',')
                    .append(escape(r.getOrderNo())).append(',')
                    .append(r.getCreatedAt() == null ? "" : r.getCreatedAt().format(TIME_FMT))
                    .append('\n');
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("roster-" + id + ".csv", StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /** CSV 转义：含逗号/引号/换行的字段加双引号包裹 */
    private String escape(String v) {
        if (v == null) {
            return "";
        }
        return (v.contains(",") || v.contains("\"") || v.contains("\n"))
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
