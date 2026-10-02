package com.mall.enroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.CoursePhaseDTO;
import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.dto.EnrolledStudentDTO;
import com.mall.common.dto.IdListRequest;
import com.mall.common.dto.ReleaseSeatRequest;
import com.mall.common.dto.SeatDTO;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.dto.UserBriefDTO;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.common.util.WeeksUtil;
import com.mall.enroll.dto.CreateEnrollRequest;
import com.mall.enroll.dto.EnrollOrderVO;
import com.mall.enroll.dto.WaitlistRequest;
import com.mall.enroll.dto.WaitlistVO;
import com.mall.enroll.dto.WishRequest;
import com.mall.enroll.dto.WishVO;
import com.mall.enroll.entity.EnrollOrder;
import com.mall.enroll.entity.EnrollWish;
import com.mall.enroll.entity.Waitlist;
import com.mall.enroll.feign.CourseFeignClient;
import com.mall.enroll.feign.SeatFeignClient;
import com.mall.enroll.feign.UserFeignClient;
import com.mall.enroll.mapper.EnrollOrderMapper;
import com.mall.enroll.mapper.EnrollWishMapper;
import com.mall.enroll.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 选课核心业务：普通选课（Seata AT）、退课、志愿填报/结算、候补队列。
 *
 * 设计要点：
 *  1. 选课前的纯校验（阶段门控、时间冲突、学分上限）全部放在 Seata 全局事务之外：
 *     校验只是读操作，进事务会白白持有全局锁、拉长事务时间，高并发下直接拖垮吞吐；
 *  2. 真正需要跨库一致性的"建单+扣名额"才进 @GlobalTransactional（见 EnrollTxService）；
 *  3. 退课/候补补位这类"先本地、后远程"的链路按顺序执行，失败时打日志告警，
 *     演示项目不引入 MQ 做最终一致性，注释中说明生产做法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollService {

    /** 学期学分上限：已选学分 + 新课学分超过该值直接拒绝 */
    private static final BigDecimal MAX_CREDITS = new BigDecimal("30");

    private final EnrollOrderMapper enrollOrderMapper;
    private final EnrollWishMapper wishMapper;
    private final WaitlistMapper waitlistMapper;
    private final CourseFeignClient courseFeignClient;
    private final SeatFeignClient seatFeignClient;
    private final UserFeignClient userFeignClient;
    private final EnrollTxService enrollTxService;

    // ---------------- 普通选课 ----------------

    /**
     * 普通选课：先做纯校验（不进事务），再进 Seata 全局事务建单+扣名额。
     * 任何一步失败，全局回滚，选课单不会残留。
     */
    public Long createEnroll(Long userId, CreateEnrollRequest req) {
        CourseDTO course = validateForEnroll(userId, req.getCourseId());
        return enrollTxService.createInTx(userId, course, 0, req.getQuantity());
    }

    /**
     * 对比接口：无 Seata。演示用——传一个名额不足的 courseId，
     * 会看到选课单残留（脏数据），而 /create 接口同样场景下选课单会被回滚。
     * 前置校验与 /create 一致，对比的是"事务"本身的效果。
     */
    public Long createEnrollNoSeata(Long userId, CreateEnrollRequest req) {
        CourseDTO course = validateForEnroll(userId, req.getCourseId());

        EnrollOrder order = EnrollTxService.buildOrder(userId, course, 0);
        enrollOrderMapper.insert(order); // 无事务包裹，立即提交，失败也回滚不了
        log.warn("选课单已直接提交 id={}", order.getId());

        Result<Void> deductResult = seatFeignClient.deduct(
                new DeductSeatRequest(req.getCourseId(), req.getQuantity()));
        if (deductResult == null || deductResult.getCode() != 200) {
            // 注意：这里即使抛异常，选课单也已经入库 -> 脏数据，Seata 版本不会这样
            throw new BizException("扣减名额失败（注意：选课单已残留，未回滚）: "
                    + (deductResult == null ? "无响应" : deductResult.getMsg()));
        }
        return order.getId();
    }

    /**
     * 秒杀抢课的建单：course-service 的 Lua 预扣成功后调这里。
     * 为什么不用 Seata？秒杀是高并发写，Seata 的全局锁 + undo_log + 两阶段提交
     * 在高并发下是性能杀手；秒杀链路用 Redis 预扣 + 失败补偿（见 SeckillService），
     * 这里只做本服务本地事务 + 同步调 seat 扣 DB 名额（DB 名额做最终兜底）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSeckillEnroll(SeckillEnrollRequest req) {
        CourseDTO course = getAvailableCourse(req.getCourseId());
        EnrollOrder order = EnrollTxService.buildOrder(req.getUserId(), course, 1);
        enrollOrderMapper.insert(order);
        Result<Void> deductResult = seatFeignClient.deduct(
                new DeductSeatRequest(req.getCourseId(), 1));
        if (deductResult == null || deductResult.getCode() != 200) {
            throw new BizException("扣减名额失败：" + (deductResult == null ? "无响应" : deductResult.getMsg()));
        }
        return order.getId();
    }

    /**
     * 选课前置校验：纯读操作，不进 Seata 事务。
     * 顺序：阶段门控 -> 课程有效 -> 去重 -> 时间冲突 -> 学分上限。
     */
    private CourseDTO validateForEnroll(Long userId, Long courseId) {
        // 1. 阶段门控：只有 MAIN（正选）/ ADD（补选）允许直接选课
        CoursePhaseDTO phase = currentPhaseOrNull();
        if (phase == null) {
            throw new BizException("当前不在选课阶段");
        }
        if ("WISH".equals(phase.getType())) {
            throw new BizException("当前为志愿填报阶段，请先填报志愿");
        }
        // 2. 课程必须存在且上架
        CourseDTO course = getAvailableCourse(courseId);
        // 3. 去重：同一门课不能重复选
        if (hasEnrolled(userId, courseId)) {
            throw new BizException("您已选过该课程，请勿重复选课");
        }
        // 4. 时间冲突：与已选课程周几相同、节次重叠、教学周重叠
        checkTimeConflict(userId, course);
        // 5. 学分上限
        checkCreditLimit(userId, course);
        return course;
    }

    private void checkTimeConflict(Long userId, CourseDTO course) {
        if (course.getWeekday() == null || course.getStartSection() == null
                || course.getEndSection() == null || course.getWeeks() == null) {
            return; // 老数据缺时间信息时跳过冲突检测，避免误杀
        }
        List<EnrollOrder> orders = myActiveOrders(userId);
        if (orders.isEmpty()) {
            return;
        }
        List<Long> courseIds = orders.stream()
                .map(EnrollOrder::getCourseId)
                .filter(id -> !id.equals(course.getId()))
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }
        Result<List<CourseDTO>> batchResult =
                courseFeignClient.batch(new IdListRequest(courseIds));
        List<CourseDTO> others = batchResult != null && batchResult.getData() != null
                ? batchResult.getData() : List.of();
        for (CourseDTO other : others) {
            if (other.getWeekday() == null || !other.getWeekday().equals(course.getWeekday())) {
                continue;
            }
            boolean sectionHit = WeeksUtil.sectionOverlap(
                    course.getStartSection(), course.getEndSection(),
                    other.getStartSection(), other.getEndSection());
            if (sectionHit && WeeksUtil.overlap(course.getWeeks(), other.getWeeks())) {
                throw new BizException("与《" + other.getName() + "》课程时间冲突（周"
                        + course.getWeekday() + " 第" + course.getStartSection()
                        + "-" + course.getEndSection() + "节）");
            }
        }
    }

    private void checkCreditLimit(Long userId, CourseDTO course) {
        BigDecimal used = myActiveOrders(userId).stream()
                .map(o -> o.getCredit() == null ? BigDecimal.ZERO : o.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = course.getCredit() == null ? BigDecimal.ZERO : course.getCredit();
        if (used.add(credit).compareTo(MAX_CREDITS) > 0) {
            throw new BizException("学分超出上限：已选 " + strip(used) + " 学分，本门 "
                    + strip(credit) + " 学分，超过 30 学分上限");
        }
    }

    private String strip(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    // ---------------- 退课 ----------------

    /**
     * 退课：仅 MAIN/ADD 阶段允许。
     * 步骤：订单置已取消（单条 UPDATE，原子）-> 调 seat-service 释放名额 -> 触发候补补位。
     * 释放名额失败会抛异常告警（订单已取消，需要人工/定时对账补释放；生产用 MQ 做最终一致性）。
     */
    public void drop(Long userId, Long orderId) {
        EnrollOrder order = enrollOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("选课单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BizException(403, "只能退自己的课");
        }
        if (order.getStatus() == null || order.getStatus() != 1) {
            throw new BizException("该选课单不在已选状态，无法退课");
        }
        CoursePhaseDTO phase = currentPhaseOrNull();
        if (phase == null) {
            throw new BizException("当前不在选课阶段，无法退课");
        }
        if (!"MAIN".equals(phase.getType()) && !"ADD".equals(phase.getType())) {
            throw new BizException("志愿填报阶段不能退课");
        }

        order.setStatus(2);
        enrollOrderMapper.updateById(order);

        Result<Void> releaseResult = seatFeignClient.release(
                new ReleaseSeatRequest(order.getCourseId(), 1));
        if (releaseResult == null || releaseResult.getCode() != 200) {
            throw new BizException("释放名额失败："
                    + (releaseResult == null ? "无响应" : releaseResult.getMsg()));
        }

        refillFromWaitlist(order.getCourseId());
    }

    /**
     * 候补补位：取该课程排队第一人，若还有名额就为其建单（orderType=3 候补转正）并扣名额，
     * 成功后把他的候补状态置为已补位。补位失败（名额被并发抢走）只打日志，不影响退课主流程。
     */
    private void refillFromWaitlist(Long courseId) {
        try {
            Waitlist first = waitlistMapper.selectOne(
                    new LambdaQueryWrapper<Waitlist>()
                            .eq(Waitlist::getCourseId, courseId)
                            .eq(Waitlist::getStatus, 0)
                            .orderByAsc(Waitlist::getPosition)
                            .last("LIMIT 1"));
            if (first == null) {
                return;
            }
            Result<SeatDTO> info = seatFeignClient.info(courseId);
            Integer available = info != null && info.getData() != null
                    ? info.getData().getAvailable() : null;
            if (available == null || available <= 0) {
                return;
            }
            CourseDTO course = getAvailableCourse(courseId);
            enrollTxService.createInTx(first.getStudentId(), course, 3, 1);
            first.setStatus(1);
            waitlistMapper.updateById(first);
            log.info("候补补位成功 courseId={} studentId={}", courseId, first.getStudentId());
        } catch (BizException e) {
            log.warn("候补补位失败，该学生继续排队 courseId={} reason={}", courseId, e.getMessage());
        }
    }

    // ---------------- 志愿填报 ----------------

    /** 填报志愿：仅 WISH 阶段；同一课程不可重复填报 */
    public Long wish(Long userId, WishRequest req) {
        CoursePhaseDTO phase = currentPhaseOrNull();
        if (phase == null) {
            throw new BizException("当前不在选课阶段");
        }
        if (!"WISH".equals(phase.getType())) {
            throw new BizException("当前不是志愿填报阶段，无法填报志愿");
        }
        getAvailableCourse(req.getCourseId());
        long dup = wishMapper.selectCount(new LambdaQueryWrapper<EnrollWish>()
                .eq(EnrollWish::getStudentId, userId)
                .eq(EnrollWish::getCourseId, req.getCourseId()));
        if (dup > 0) {
            throw new BizException("您已填报过该课程的志愿");
        }
        EnrollWish wish = new EnrollWish();
        wish.setStudentId(userId);
        wish.setCourseId(req.getCourseId());
        wish.setPriority(req.getPriority());
        wish.setCreatedAt(LocalDateTime.now());
        wishMapper.insert(wish);
        return wish.getId();
    }

    /** 我的志愿：带课程名和上课时间 */
    public List<WishVO> myWishes(Long userId) {
        List<EnrollWish> wishes = wishMapper.selectList(
                new LambdaQueryWrapper<EnrollWish>()
                        .eq(EnrollWish::getStudentId, userId)
                        .orderByDesc(EnrollWish::getId));
        Map<Long, CourseDTO> courseMap = batchCourseMap(
                wishes.stream().map(EnrollWish::getCourseId).distinct().toList());
        return wishes.stream().map(w -> {
            CourseDTO c = courseMap.get(w.getCourseId());
            return new WishVO(w.getId(), w.getCourseId(),
                    c == null ? "" : c.getName(), w.getPriority(),
                    c == null ? null : c.getWeekday(),
                    c == null ? null : c.getStartSection(),
                    c == null ? null : c.getEndSection(),
                    c == null ? null : c.getWeeks(),
                    c == null ? null : c.getClassroom(),
                    w.getCreatedAt());
        }).toList();
    }

    public void deleteWish(Long userId, Long wishId) {
        EnrollWish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BizException("志愿不存在");
        }
        if (!userId.equals(wish.getStudentId())) {
            throw new BizException(403, "只能删除自己的志愿");
        }
        wishMapper.deleteById(wishId);
    }

    // ---------------- 候补队列 ----------------

    /**
     * 加入候补：只有"名额已满"的课程才能候补，有名额时直接提示去选课。
     * position 按课程内单调递增，保证先到先补位。
     */
    public Long joinWaitlist(Long userId, WaitlistRequest req) {
        getAvailableCourse(req.getCourseId());
        if (hasEnrolled(userId, req.getCourseId())) {
            throw new BizException("您已选上该课程，无需候补");
        }
        long queuing = waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getStudentId, userId)
                .eq(Waitlist::getCourseId, req.getCourseId())
                .eq(Waitlist::getStatus, 0));
        if (queuing > 0) {
            throw new BizException("您已在该课程的候补队列中");
        }
        Result<SeatDTO> info = seatFeignClient.info(req.getCourseId());
        Integer available = info != null && info.getData() != null
                ? info.getData().getAvailable() : null;
        if (available == null) {
            throw new BizException("名额服务无响应，请稍后重试");
        }
        if (available > 0) {
            throw new BizException("该课程还有名额，请直接选课，无需候补");
        }
        Waitlist w = new Waitlist();
        w.setStudentId(userId);
        w.setCourseId(req.getCourseId());
        w.setPosition(waitlistMapper.maxPosition(req.getCourseId()) + 1);
        w.setStatus(0);
        w.setCreatedAt(LocalDateTime.now());
        waitlistMapper.insert(w);
        return w.getId();
    }

    /** 我的候补：带课程名和排队位置 */
    public List<WaitlistVO> myWaitlist(Long userId) {
        List<Waitlist> list = waitlistMapper.selectList(
                new LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getStudentId, userId)
                        .eq(Waitlist::getStatus, 0)
                        .orderByAsc(Waitlist::getPosition));
        Map<Long, CourseDTO> courseMap = batchCourseMap(
                list.stream().map(Waitlist::getCourseId).distinct().toList());
        return list.stream().map(w -> {
            CourseDTO c = courseMap.get(w.getCourseId());
            return new WaitlistVO(w.getId(), w.getCourseId(),
                    c == null ? "" : c.getName(), w.getPosition(), w.getCreatedAt());
        }).toList();
    }

    /** 取消候补：状态置 2（已取消），保留记录备查 */
    public void cancelWaitlist(Long userId, Long waitlistId) {
        Waitlist w = waitlistMapper.selectById(waitlistId);
        if (w == null) {
            throw new BizException("候补记录不存在");
        }
        if (!userId.equals(w.getStudentId())) {
            throw new BizException(403, "只能取消自己的候补");
        }
        w.setStatus(2);
        waitlistMapper.updateById(w);
    }

    // ---------------- 志愿结算（管理端） ----------------

    /**
     * 志愿结算：按课程分组，组内按 (priority ASC, createdAt ASC) 逐个录取。
     * 每个人一次 @GlobalTransactional（建单 orderType=2 + 远程扣名额）；
     * 名额不足抛异常时该生转入候补队列，继续下一个人，互不影响。
     * 结算完成后把阶段置为已结束。
     */
    public Map<String, Integer> settleWishes(Long phaseId) {
        Result<CoursePhaseDTO> phaseResult = courseFeignClient.phaseById(phaseId);
        CoursePhaseDTO phase = phaseResult != null ? phaseResult.getData() : null;
        if (phase == null) {
            throw new BizException("选课阶段不存在");
        }
        if (!"WISH".equals(phase.getType())) {
            throw new BizException("只能结算志愿填报阶段");
        }

        List<EnrollWish> wishes = wishMapper.selectList(
                new LambdaQueryWrapper<EnrollWish>()
                        .orderByAsc(EnrollWish::getCourseId)
                        .orderByAsc(EnrollWish::getPriority)
                        .orderByAsc(EnrollWish::getCreatedAt));
        // 按课程分组：LinkedHashMap 保持课程顺序稳定
        Map<Long, List<EnrollWish>> byCourse = wishes.stream()
                .collect(Collectors.groupingBy(EnrollWish::getCourseId,
                        LinkedHashMap::new, Collectors.toList()));

        int admitted = 0;
        int waitlisted = 0;
        for (Map.Entry<Long, List<EnrollWish>> entry : byCourse.entrySet()) {
            CourseDTO course = safeGetCourse(entry.getKey());
            if (course == null || course.getStatus() == null || course.getStatus() != 1) {
                log.warn("志愿结算跳过课程：不存在或已下架 courseId={}", entry.getKey());
                continue;
            }
            for (EnrollWish wish : entry.getValue()) {
                try {
                    enrollTxService.createInTx(wish.getStudentId(), course, 2, 1);
                    wishMapper.deleteById(wish.getId());
                    admitted++;
                } catch (BizException e) {
                    if (e.getMessage() != null && e.getMessage().contains("名额不足")) {
                        // 名额不够：转入候补队列，按排队顺序递增 position
                        moveToWaitlist(wish.getStudentId(), course.getId());
                        wishMapper.deleteById(wish.getId());
                        waitlisted++;
                    } else {
                        // 其他异常（服务抖动等）：保留志愿记录，跳过此人继续结算
                        log.warn("志愿录取失败，跳过 wishId={} reason={}",
                                wish.getId(), e.getMessage());
                    }
                }
            }
        }
        courseFeignClient.finishPhase(phaseId);
        log.info("志愿结算完成 phaseId={} admitted={} waitlisted={}", phaseId, admitted, waitlisted);
        return Map.of("admitted", admitted, "waitlisted", waitlisted);
    }

    private void moveToWaitlist(Long studentId, Long courseId) {
        long exists = waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getStudentId, studentId)
                .eq(Waitlist::getCourseId, courseId)
                .eq(Waitlist::getStatus, 0));
        if (exists > 0) {
            return;
        }
        Waitlist w = new Waitlist();
        w.setStudentId(studentId);
        w.setCourseId(courseId);
        w.setPosition(waitlistMapper.maxPosition(courseId) + 1);
        w.setStatus(0);
        w.setCreatedAt(LocalDateTime.now());
        waitlistMapper.insert(w);
    }

    // ---------------- 我的选课单 ----------------

    /** 我的选课单：订单字段 + 课程上课时间/教室/教师名 */
    public List<EnrollOrderVO> myOrderVOs(Long userId) {
        List<EnrollOrder> orders = enrollOrderMapper.selectList(
                new LambdaQueryWrapper<EnrollOrder>()
                        .eq(EnrollOrder::getUserId, userId)
                        .orderByDesc(EnrollOrder::getId));
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseDTO> courseMap = batchCourseMap(
                orders.stream().map(EnrollOrder::getCourseId).distinct().toList());

        // 教师名：course.teacherId -> user-service 批量换 nickname，拿不到回退 course.teacher 快照
        List<Long> teacherIds = courseMap.values().stream()
                .map(CourseDTO::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserBriefDTO> teacherMap = Map.of();
        if (!teacherIds.isEmpty()) {
            Result<Map<Long, UserBriefDTO>> teacherResult =
                    userFeignClient.batch(new IdListRequest(teacherIds));
            if (teacherResult != null && teacherResult.getData() != null) {
                teacherMap = teacherResult.getData();
            }
        }
        Map<Long, UserBriefDTO> finalTeacherMap = teacherMap;
        return orders.stream().map(o -> {
            CourseDTO c = courseMap.get(o.getCourseId());
            String teacherName = c == null ? "" : c.getTeacher();
            if (c != null && c.getTeacherId() != null) {
                UserBriefDTO t = finalTeacherMap.get(c.getTeacherId());
                if (t != null && t.getNickname() != null && !t.getNickname().isBlank()) {
                    teacherName = t.getNickname();
                }
            }
            return new EnrollOrderVO(o.getId(), o.getOrderNo(), o.getUserId(), o.getCourseId(),
                    o.getCourseName(), o.getCredit(), o.getOrderType(), o.getStatus(), o.getCreatedAt(),
                    c == null ? null : c.getWeekday(),
                    c == null ? null : c.getStartSection(),
                    c == null ? null : c.getEndSection(),
                    c == null ? null : c.getWeeks(),
                    c == null ? null : c.getClassroom(),
                    teacherName);
        }).toList();
    }

    // ---------------- 内部接口（服务间调用） ----------------

    /** 该生是否有 status=1 的选课单：课程评价资格核验用 */
    public boolean hasEnrolled(Long studentId, Long courseId) {
        return enrollOrderMapper.selectCount(new LambdaQueryWrapper<EnrollOrder>()
                .eq(EnrollOrder::getUserId, studentId)
                .eq(EnrollOrder::getCourseId, courseId)
                .eq(EnrollOrder::getStatus, 1)) > 0;
    }

    /** 某课程已选上的学生：教师端花名册用 */
    public List<EnrolledStudentDTO> courseStudents(Long courseId) {
        return enrollOrderMapper.selectList(new LambdaQueryWrapper<EnrollOrder>()
                        .eq(EnrollOrder::getCourseId, courseId)
                        .eq(EnrollOrder::getStatus, 1)
                        .orderByAsc(EnrollOrder::getId))
                .stream()
                .map(o -> new EnrolledStudentDTO(o.getUserId(), o.getOrderNo(), o.getCreatedAt()))
                .toList();
    }

    /** 各课程选上人数 {courseId: count}：管理端看板用 */
    public Map<Long, Integer> countsByCourse() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : enrollOrderMapper.countByCourse()) {
            Long courseId = ((Number) row.get("courseId")).longValue();
            Integer cnt = ((Number) row.get("cnt")).intValue();
            result.put(courseId, cnt);
        }
        return result;
    }

    // ---------------- 内部工具 ----------------

    private List<EnrollOrder> myActiveOrders(Long userId) {
        return enrollOrderMapper.selectList(new LambdaQueryWrapper<EnrollOrder>()
                .eq(EnrollOrder::getUserId, userId)
                .eq(EnrollOrder::getStatus, 1));
    }

    private Map<Long, CourseDTO> batchCourseMap(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        Result<List<CourseDTO>> result = courseFeignClient.batch(new IdListRequest(courseIds));
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        return result.getData().stream()
                .collect(Collectors.toMap(CourseDTO::getId, c -> c, (a, b) -> a));
    }

    private CourseDTO safeGetCourse(Long courseId) {
        try {
            Result<CourseDTO> result = courseFeignClient.getById(courseId);
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.warn("查询课程失败 courseId={}", courseId, e);
            return null;
        }
    }

    private CoursePhaseDTO currentPhaseOrNull() {
        Result<CoursePhaseDTO> result = courseFeignClient.currentPhase();
        return result == null ? null : result.getData();
    }

    /** 课程必须存在且上架，否则不允许建单 */
    private CourseDTO getAvailableCourse(Long courseId) {
        Result<CourseDTO> result = courseFeignClient.getById(courseId);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new BizException("课程不存在");
        }
        CourseDTO course = result.getData();
        if (course.getStatus() == null || course.getStatus() != 1) {
            throw new BizException("课程已下架，不可选");
        }
        return course;
    }
}
