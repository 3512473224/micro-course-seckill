package com.mall.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.constant.RedisKeys;
import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.CoursePhaseDTO;
import com.mall.common.dto.EnrolledStudentDTO;
import com.mall.common.dto.IdListRequest;
import com.mall.common.dto.SeatDTO;
import com.mall.common.dto.UserBriefDTO;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.common.util.WeeksUtil;
import com.mall.course.dto.CreateCourseRequest;
import com.mall.course.dto.DashboardItemVO;
import com.mall.course.dto.PhaseRequest;
import com.mall.course.dto.ReviewSummaryVO;
import com.mall.course.dto.RosterItemVO;
import com.mall.course.dto.UpdateCourseRequest;
import com.mall.course.entity.Course;
import com.mall.course.entity.CoursePhase;
import com.mall.course.entity.CourseReview;
import com.mall.course.feign.EnrollFeignClient;
import com.mall.course.feign.UserFeignClient;
import com.mall.course.mapper.CourseMapper;
import com.mall.course.mapper.CoursePhaseMapper;
import com.mall.course.mapper.CourseReviewMapper;
import com.mall.course.mapper.SeatManageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程查询：典型的"读多写少"场景，用 Cache-Aside 缓存。
 *
 * 为什么这样写：
 *  1. 先查 Redis，命中直接返回，扛住选课季首页的大量读请求，保护 MySQL；
 *  2. 未命中再查 DB 并回填缓存（TTL 5 分钟），避免缓存长期不一致；
 *  3. 写操作（开课/改课/上下架）后必须删缓存，否则 5 分钟内读到旧数据。
 *
 * 缓存常见问题与对策：
 *  - 穿透：查不存在的 id，每次都打到 DB。解：缓存空值（短 TTL）/ 布隆过滤器。
 *  - 击穿：热点 key 过期瞬间大量请求打到 DB。解：互斥锁 / 逻辑过期。
 *  - 雪崩：大量 key 同时过期。解：TTL 加随机值 / 多级缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final CoursePhaseMapper phaseMapper;
    private final CourseReviewMapper reviewMapper;
    private final SeatManageMapper seatManageMapper;
    private final EnrollFeignClient enrollFeignClient;
    private final UserFeignClient userFeignClient;
    private final RedisTemplate<String, String> redisTemplate;

    /** 注意：为演示简洁，这里用 JSON 字符串手写序列化；生产可用 RedisTemplate<String, Object> + Jackson */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Set<String> PHASE_TYPES = Set.of("WISH", "MAIN", "ADD");

    // ---------------- 课程读（缓存） ----------------

    public List<Course> list() {
        String key = RedisKeys.courseList();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return readList(cached);
        }
        List<Course> list = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getStatus, 1).orderByDesc(Course::getId));
        redisTemplate.opsForValue().set(key, writeJson(list), Duration.ofMinutes(5));
        return list;
    }

    public Course getById(Long id) {
        String key = RedisKeys.course(id);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return readOne(cached);
        }
        Course course = courseMapper.selectById(id);
        if (course != null) {
            redisTemplate.opsForValue().set(key, writeJson(course), Duration.ofMinutes(5));
        }
        return course;
    }

    /** 批量查课程：enroll-service 做时间冲突/学分校验时一次拉取，避免 N+1 远程调用 */
    public List<CourseDTO> batch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectBatchIds(ids).stream()
                .map(this::toDTO)
                .toList();
    }

    public CourseDTO toDTO(Course c) {
        CourseDTO dto = new CourseDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setTeacher(c.getTeacher());
        dto.setCredit(c.getCredit());
        dto.setStatus(c.getStatus());
        dto.setWeekday(c.getWeekday());
        dto.setStartSection(c.getStartSection());
        dto.setEndSection(c.getEndSection());
        dto.setWeeks(c.getWeeks());
        dto.setClassroom(c.getClassroom());
        dto.setTeacherId(c.getTeacherId());
        return dto;
    }

    // ---------------- 选课阶段 ----------------

    /** 当前进行中的阶段：status=1 且 now 落在 [startTime, endTime] 内；无则返回 null */
    public CoursePhaseDTO currentPhase() {
        LocalDateTime now = LocalDateTime.now();
        CoursePhase phase = phaseMapper.selectOne(
                new LambdaQueryWrapper<CoursePhase>()
                        .eq(CoursePhase::getStatus, 1)
                        .le(CoursePhase::getStartTime, now)
                        .ge(CoursePhase::getEndTime, now)
                        .orderByDesc(CoursePhase::getId)
                        .last("LIMIT 1"));
        return phase == null ? null : toPhaseDTO(phase);
    }

    public List<CoursePhaseDTO> listPhases() {
        return phaseMapper.selectList(
                        new LambdaQueryWrapper<CoursePhase>().orderByDesc(CoursePhase::getId))
                .stream().map(this::toPhaseDTO).toList();
    }

    public CoursePhaseDTO getPhase(Long id) {
        CoursePhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("选课阶段不存在");
        }
        return toPhaseDTO(phase);
    }

    public Long createPhase(PhaseRequest req) {
        checkPhaseRequest(req);
        CoursePhase phase = new CoursePhase();
        phase.setName(req.getName());
        phase.setType(req.getType());
        phase.setStartTime(req.getStartTime());
        phase.setEndTime(req.getEndTime());
        phase.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        phase.setCreatedAt(LocalDateTime.now());
        phaseMapper.insert(phase);
        return phase.getId();
    }

    public void updatePhase(Long id, PhaseRequest req) {
        CoursePhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("选课阶段不存在");
        }
        checkPhaseRequest(req);
        phase.setName(req.getName());
        phase.setType(req.getType());
        phase.setStartTime(req.getStartTime());
        phase.setEndTime(req.getEndTime());
        if (req.getStatus() != null) {
            phase.setStatus(req.getStatus());
        }
        phaseMapper.updateById(phase);
    }

    /** 开关：0=关闭 <-> 1=进行中（已结束的阶段不允许再打开，避免阶段回退造成数据混乱） */
    public int togglePhase(Long id) {
        CoursePhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("选课阶段不存在");
        }
        if (phase.getStatus() == 2) {
            throw new BizException("已结束的阶段不能重新打开");
        }
        int next = phase.getStatus() == 1 ? 0 : 1;
        phase.setStatus(next);
        phaseMapper.updateById(phase);
        return next;
    }

    /** 志愿结算完成后调用：把阶段置为已结束（2） */
    public void finishPhase(Long id) {
        CoursePhase phase = phaseMapper.selectById(id);
        if (phase == null) {
            throw new BizException("选课阶段不存在");
        }
        phase.setStatus(2);
        phaseMapper.updateById(phase);
    }

    private void checkPhaseRequest(PhaseRequest req) {
        if (!PHASE_TYPES.contains(req.getType())) {
            throw new BizException("阶段类型只能是 WISH/MAIN/ADD");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new BizException("开始时间必须早于结束时间");
        }
    }

    private CoursePhaseDTO toPhaseDTO(CoursePhase p) {
        return new CoursePhaseDTO(p.getId(), p.getName(), p.getType(),
                p.getStartTime(), p.getEndTime(), p.getStatus());
    }

    // ---------------- 课程评价 ----------------

    /**
     * 发表评价：先调 enroll-service 核验"该生确实选上过这门课"，防止刷评价。
     * 同一学生一门课只能评一次（先查后插 + UK 兜底）。
     */
    public void addReview(Long studentId, Long courseId, Integer score, String comment) {
        Result<Boolean> check = enrollFeignClient.check(studentId, courseId);
        if (check == null || check.getCode() != 200 || !Boolean.TRUE.equals(check.getData())) {
            throw new BizException(403, "只有选上该课程的学生才能评价");
        }
        long exists = reviewMapper.selectCount(
                new LambdaQueryWrapper<CourseReview>()
                        .eq(CourseReview::getStudentId, studentId)
                        .eq(CourseReview::getCourseId, courseId));
        if (exists > 0) {
            throw new BizException("您已评价过该课程，不能重复评价");
        }
        CourseReview review = new CourseReview();
        review.setStudentId(studentId);
        review.setCourseId(courseId);
        review.setScore(score);
        review.setComment(comment == null ? "" : comment);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
    }

    public ReviewSummaryVO reviewSummary(Long courseId) {
        List<CourseReview> list = reviewMapper.selectList(
                new LambdaQueryWrapper<CourseReview>()
                        .eq(CourseReview::getCourseId, courseId)
                        .orderByDesc(CourseReview::getId));
        Double avg = reviewMapper.avgScore(courseId);
        return new ReviewSummaryVO(avg == null ? 0.0 : Math.round(avg * 10) / 10.0,
                list.size(), list);
    }

    // ---------------- 教师端 ----------------

    /**
     * 开课：教师开的是草稿（status=0），管理员开的直接发布（status=1），由调用方传入。
     * 同时初始化 seat 表（total=available=capacity），否则选课时查不到名额行。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createCourse(Long teacherId, String teacherName, CreateCourseRequest req, int status) {
        checkCourseTime(req.getWeekday(), req.getStartSection(), req.getEndSection(), req.getWeeks());
        Course course = new Course();
        course.setName(req.getName());
        course.setTeacher(teacherName == null ? "" : teacherName);
        course.setCredit(req.getCredit());
        course.setDescription(req.getDescription() == null ? "" : req.getDescription());
        course.setStatus(status);
        course.setWeekday(req.getWeekday());
        course.setStartSection(req.getStartSection());
        course.setEndSection(req.getEndSection());
        course.setWeeks(req.getWeeks() == null ? "1-16" : req.getWeeks());
        course.setClassroom(req.getClassroom() == null ? "" : req.getClassroom());
        course.setTeacherId(teacherId);
        course.setCreatedAt(LocalDateTime.now());
        courseMapper.insert(course);
        seatManageMapper.insertSeat(course.getId(), req.getCapacity(), req.getCapacity());
        evictListCache();
        return course.getId();
    }

    public List<Course> myCourses(Long teacherId) {
        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId)
                        .orderByDesc(Course::getId));
    }

    /**
     * 花名册：先验"这门课真是你的"，再调 enroll-service 取选课学生 id，
     * 最后调 user-service 批量换姓名——两次远程调用都是批量的，没有 N+1。
     */
    public List<RosterItemVO> roster(Long courseId, Long teacherId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException("课程不存在");
        }
        if (!teacherId.equals(course.getTeacherId())) {
            throw new BizException(403, "只能查看自己所授课程的花名册");
        }
        Result<List<EnrolledStudentDTO>> studentsResult = enrollFeignClient.courseStudents(courseId);
        List<EnrolledStudentDTO> students = (studentsResult != null && studentsResult.getData() != null)
                ? studentsResult.getData() : List.of();
        Map<Long, UserBriefDTO> users = Map.of();
        if (!students.isEmpty()) {
            List<Long> ids = students.stream().map(EnrolledStudentDTO::getUserId).toList();
            Result<Map<Long, UserBriefDTO>> batchResult =
                    userFeignClient.batch(new IdListRequest(ids));
            if (batchResult != null && batchResult.getData() != null) {
                users = batchResult.getData();
            }
        }
        Map<Long, UserBriefDTO> finalUsers = users;
        return students.stream().map(s -> {
            UserBriefDTO u = finalUsers.get(s.getUserId());
            return new RosterItemVO(s.getUserId(),
                    u == null ? "" : u.getUsername(),
                    u == null ? "" : u.getNickname(),
                    s.getOrderNo(), s.getCreatedAt());
        }).toList();
    }

    // ---------------- 管理端 ----------------

    /** 改课：时间/教室/容量/上下架。容量变化时按"已用名额不变"重算 available，避免凭空增减已选人数 */
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(Long id, UpdateCourseRequest req) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BizException("课程不存在");
        }
        if (req.getWeekday() != null) {
            course.setWeekday(req.getWeekday());
        }
        if (req.getStartSection() != null) {
            course.setStartSection(req.getStartSection());
        }
        if (req.getEndSection() != null) {
            course.setEndSection(req.getEndSection());
        }
        if (req.getWeeks() != null) {
            course.setWeeks(req.getWeeks());
        }
        checkCourseTime(course.getWeekday(), course.getStartSection(),
                course.getEndSection(), course.getWeeks());
        if (req.getClassroom() != null) {
            course.setClassroom(req.getClassroom());
        }
        if (req.getStatus() != null) {
            course.setStatus(req.getStatus());
        }
        if (req.getCapacity() != null && req.getCapacity() > 0) {
            adjustCapacity(id, req.getCapacity());
        }
        courseMapper.updateById(course);
        evictCourseCache(id);
    }

    private void adjustCapacity(Long courseId, int newCapacity) {
        SeatDTO seat = seatManageMapper.selectSeat(courseId);
        if (seat == null) {
            seatManageMapper.insertSeat(courseId, newCapacity, newCapacity);
            return;
        }
        int used = seat.getTotal() - seat.getAvailable();
        int newAvailable = Math.max(0, newCapacity - used);
        seatManageMapper.updateCapacity(courseId, newCapacity, newAvailable);
    }

    /** 上下架：发布=1，下架=0；写后删缓存，否则列表页 5 分钟内还是旧状态 */
    public void setStatus(Long id, int status) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BizException("课程不存在");
        }
        course.setStatus(status);
        courseMapper.updateById(course);
        evictCourseCache(id);
    }

    /**
     * 数据看板：每门课的总名额/已选人数/选课率/平均分，按已选人数倒序。
     * 已选人数调 enroll-service 的 counts 批量接口一次拿全，避免逐门查。
     */
    public List<DashboardItemVO> dashboard() {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().orderByDesc(Course::getId));
        Map<Long, Integer> counts = Map.of();
        Result<Map<Long, Integer>> countsResult = enrollFeignClient.counts();
        if (countsResult != null && countsResult.getData() != null) {
            counts = countsResult.getData();
        }
        List<DashboardItemVO> items = new ArrayList<>();
        for (Course c : courses) {
            SeatDTO seat = seatManageMapper.selectSeat(c.getId());
            int total = seat == null ? 0 : seat.getTotal();
            int enrolled = counts.getOrDefault(c.getId(), 0);
            double rate = total > 0 ? Math.round(enrolled * 1000.0 / total) / 10.0 : 0.0;
            Double avg = reviewMapper.avgScore(c.getId());
            items.add(new DashboardItemVO(c.getId(), c.getName(), total, enrolled, rate,
                    avg == null ? 0.0 : Math.round(avg * 10) / 10.0));
        }
        items.sort(Comparator.comparingInt(DashboardItemVO::getEnrolled).reversed());
        return items;
    }

    // ---------------- 校验与缓存 ----------------

    private void checkCourseTime(Integer weekday, Integer start, Integer end, String weeks) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            throw new BizException("上课星期取值 1-7");
        }
        if (start == null || end == null || start < 1 || end < start) {
            throw new BizException("节次区间非法：起始节次必须 <= 结束节次");
        }
        try {
            WeeksUtil.parse(weeks);
        } catch (IllegalArgumentException e) {
            throw new BizException("教学周格式非法，应如 1-16 或 1-8,10-16");
        }
    }

    /** 写操作后删缓存：删单课程 key + 列表 key */
    private void evictCourseCache(Long id) {
        redisTemplate.delete(RedisKeys.course(id));
        evictListCache();
    }

    private void evictListCache() {
        redisTemplate.delete(RedisKeys.courseList());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private List<Course> readList(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Course.class));
        } catch (Exception e) {
            log.warn("课程列表缓存反序列化失败，降级查库", e);
            return courseMapper.selectList(
                    new LambdaQueryWrapper<Course>().eq(Course::getStatus, 1).orderByDesc(Course::getId));
        }
    }

    private Course readOne(String json) {
        try {
            return objectMapper.readValue(json, Course.class);
        } catch (Exception e) {
            log.warn("课程缓存反序列化失败", e);
            return null;
        }
    }
}
