package com.mall.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.constant.RedisKeys;
import com.mall.course.entity.Course;
import com.mall.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 课程查询：典型的"读多写少"场景，用 Cache-Aside 缓存。
 *
 * 为什么这样写：
 *  1. 先查 Redis，命中直接返回，扛住选课季首页的大量读请求，保护 MySQL；
 *  2. 未命中再查 DB 并回填缓存（TTL 5 分钟），避免缓存长期不一致；
 *  3. 课程变更（生产：管理端改课程）时要删缓存，演示项目省略写接口，注释说明。
 *
 * 面试官可能追问的三个缓存问题（背下来）：
 *  - 穿透：查不存在的 id，每次都打到 DB。解：缓存空值（短 TTL）/ 布隆过滤器。
 *  - 击穿：热点 key 过期瞬间大量请求打到 DB。解：互斥锁 / 逻辑过期。
 *  - 雪崩：大量 key 同时过期。解：TTL 加随机值 / 多级缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /** 注意：为演示简洁，这里用 JSON 字符串手写序列化；生产可用 RedisTemplate<String, Object> + Jackson */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
