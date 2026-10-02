package com.mall.enroll.controller;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.dto.EnrolledStudentDTO;
import com.mall.common.result.Result;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.enroll.dto.CreateEnrollRequest;
import com.mall.enroll.dto.EnrollOrderVO;
import com.mall.enroll.dto.WaitlistRequest;
import com.mall.enroll.dto.WaitlistVO;
import com.mall.enroll.dto.WishRequest;
import com.mall.enroll.dto.WishVO;
import com.mall.enroll.service.EnrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 选课接口 */
@RestController
@RequestMapping("/api/enroll")
@RequiredArgsConstructor
public class EnrollController {

    private final EnrollService enrollService;

    /**
     * 普通选课：Seata AT 分布式事务（本地建单 + 远程扣名额）。
     * 入口先做阶段门控/时间冲突/学分上限等纯校验（不进事务），再进全局事务。
     */
    @PostMapping("/create")
    public Result<Long> create(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                              @Valid @RequestBody CreateEnrollRequest request) {
        return Result.ok(enrollService.createEnroll(userId, request));
    }

    /**
     * 对比接口：无 Seata。演示用——传一个名额不足的 courseId，
     * 会看到选课单残留（脏数据），而 /create 接口同样场景下选课单会被回滚。
     */
    @PostMapping("/create-no-seata")
    public Result<Long> createNoSeata(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                                     @Valid @RequestBody CreateEnrollRequest request) {
        return Result.ok(enrollService.createEnrollNoSeata(userId, request));
    }

    /** 供 course-service 秒杀链路内部调用：userId 由请求体携带（服务间调用） */
    @PostMapping("/seckill")
    public Result<Long> seckill(@RequestBody SeckillEnrollRequest request) {
        return Result.ok(enrollService.createSeckillEnroll(request));
    }

    /** 退课：仅正选/补选阶段；退课后自动触发候补补位 */
    @PostMapping("/drop/{orderId}")
    public Result<Void> drop(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                             @PathVariable Long orderId) {
        enrollService.drop(userId, orderId);
        return Result.ok();
    }

    /** 我的选课单：订单 + 课程上课时间/教室/教师名 */
    @GetMapping("/my")
    public Result<List<EnrollOrderVO>> my(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(enrollService.myOrderVOs(userId));
    }

    // ---------------- 志愿填报 ----------------

    /** 填报志愿：仅志愿填报阶段 */
    @PostMapping("/wish")
    public Result<Long> wish(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                             @Valid @RequestBody WishRequest request) {
        return Result.ok(enrollService.wish(userId, request));
    }

    /** 我的志愿：带课程名和上课时间 */
    @GetMapping("/wish/my")
    public Result<List<WishVO>> myWishes(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(enrollService.myWishes(userId));
    }

    @DeleteMapping("/wish/{id}")
    public Result<Void> deleteWish(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                                   @PathVariable Long id) {
        enrollService.deleteWish(userId, id);
        return Result.ok();
    }

    // ---------------- 候补队列 ----------------

    /** 加入候补：仅名额已满的课程可候补 */
    @PostMapping("/waitlist")
    public Result<Long> joinWaitlist(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                                    @Valid @RequestBody WaitlistRequest request) {
        return Result.ok(enrollService.joinWaitlist(userId, request));
    }

    /** 我的候补：带课程名和排队位置 */
    @GetMapping("/waitlist/my")
    public Result<List<WaitlistVO>> myWaitlist(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return Result.ok(enrollService.myWaitlist(userId));
    }

    /** 取消候补 */
    @DeleteMapping("/waitlist/{id}")
    public Result<Void> cancelWaitlist(@RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
                                       @PathVariable Long id) {
        enrollService.cancelWaitlist(userId, id);
        return Result.ok();
    }

    // ---------------- 管理端 ----------------

    /**
     * 志愿结算：按 (志愿优先级, 填报时间) 逐个录取，名额不足转候补。
     * 路径以 /api/enroll/admin 开头，网关已强制要求 admin 角色。
     */
    @PostMapping("/admin/wish/settle")
    public Result<Map<String, Integer>> settle(@RequestParam Long phaseId) {
        return Result.ok(enrollService.settleWishes(phaseId));
    }

    // ---------------- 内部接口（服务间调用，不走网关） ----------------

    /** 该生是否选上过该课：课程评价资格核验用 */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long studentId,
                                 @RequestParam Long courseId) {
        return Result.ok(enrollService.hasEnrolled(studentId, courseId));
    }

    /** 某课程已选上的学生：教师端花名册用 */
    @GetMapping("/course/{courseId}/students")
    public Result<List<EnrolledStudentDTO>> courseStudents(@PathVariable Long courseId) {
        return Result.ok(enrollService.courseStudents(courseId));
    }

    /** 各课程选上人数：管理端看板用 */
    @GetMapping("/admin/counts")
    public Result<Map<Long, Integer>> counts() {
        return Result.ok(enrollService.countsByCourse());
    }
}
