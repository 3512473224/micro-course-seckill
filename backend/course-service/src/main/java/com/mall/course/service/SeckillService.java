package com.mall.course.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.mall.common.constant.LuaScripts;
import com.mall.common.constant.RedisKeys;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.dto.SeckillResult;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.course.config.SeckillBlockHandler;
import com.mall.course.entity.SeckillCourse;
import com.mall.course.feign.EnrollFeignClient;
import com.mall.course.mapper.SeckillCourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 秒杀抢课核心链路：
 *
 *  1. 校验活动时间窗（DB 查 seckill_course，低频操作可接受）；
 *  2. SETNX 限购：seckill:limit:{courseId}:{userId}，同一学生限抢 1 个名额，重复请求直接拒绝；
 *  3. Lua 脚本原子扣减 Redis 预扣名额（防超卖的核心，见 LuaScripts 注释）；
 *  4. Feign 调 enroll-service 创建选课单；若失败，补偿回滚 Redis 名额 + 删除限购标记。
 *
 * 为什么不直接扣 DB？秒杀瞬间几万 QPS 打到 MySQL，行锁排队会导致大量超时；
 * Redis 单线程 + Lua 原子操作，单机轻松扛 10w+ QPS，DB 只承担最终一致性的写入。
 *
 * 为什么这里不用 Seata？Seata AT 要在多个 DB 上加全局锁、写 undo_log，
 * 高并发下是性能杀手。秒杀链路用"Redis 预扣 + 失败补偿回滚"，最终一致性即可；
 * Seata 只用在低并发的普通选课链路（见 enroll-service）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillCourseMapper seckillCourseMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final EnrollFeignClient enrollFeignClient;

    /**
     * @SentinelResource value="seckill" 与 SentinelRuleConfig 里的规则名对应；
     * 被限流时走 SeckillBlockHandler.seckillBlock 快速失败，而不是把请求堆积拖垮服务。
     */
    @SentinelResource(value = "seckill",
            blockHandlerClass = SeckillBlockHandler.class, blockHandler = "seckillBlock")
    public SeckillResult doSeckill(Long courseId, Long userId) {
        // 1. 活动校验
        SeckillCourse sc = seckillCourseMapper.selectById(courseId);
        if (sc == null || sc.getStatus() != 1) {
            throw new BizException("抢课活动不存在或未开始");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sc.getStartTime()) || now.isAfter(sc.getEndTime())) {
            throw new BizException("不在抢课时间范围内");
        }

        // 2. 每人限抢 1 个名额：SETNX 原子，key 带 2 小时过期防脏数据堆积
        String limitKey = RedisKeys.seckillLimit(courseId, userId);
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", Duration.ofHours(2));
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException("每人限抢 1 个名额，您已参与过本次抢课");
        }

        // 3. Lua 原子扣减 Redis 名额
        String stockKey = RedisKeys.seckillStock(courseId);
        Long ok = redisTemplate.execute(LuaScripts.SECKILL_SCRIPT,
                Collections.singletonList(stockKey), "1");
        if (ok == null || ok == 0) {
            // 名额不足：删掉限购标记（允许用户去抢别的课程），不抛"重复参与"误导
            redisTemplate.delete(limitKey);
            throw new BizException("手慢了，名额已抢光");
        }

        // 4. 创建选课单（调 enroll-service）
        try {
            Result<Long> result = enrollFeignClient
                    .createSeckillEnroll(new SeckillEnrollRequest(userId, courseId));
            if (result == null || result.getCode() != 200) {
                throw new BizException("创建选课单失败：" + (result == null ? "无响应" : result.getMsg()));
            }
            log.info("抢课成功 userId={} courseId={} enrollId={}", userId, courseId, result.getData());
            return new SeckillResult(true, "抢课成功！", result.getData());
        } catch (Exception e) {
            // 补偿回滚：创建选课单失败时把 Redis 名额加回去、删除限购标记。
            // 生产级做法是用 MQ（RocketMQ 事务消息）做最终一致性，这里为演示简洁用同步补偿。
            log.error("抢课创建选课单失败，补偿回滚 Redis 名额 userId={} courseId={}", userId, courseId, e);
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(limitKey);
            throw new BizException("抢课失败，请重试（" + rootMsg(e) + "）");
        }
    }

    /** 预热：把 DB 里秒杀课程的名额数加载到 Redis。生产由定时任务在开抢前执行。 */
    public void initStock(Long courseId) {
        SeckillCourse sc = seckillCourseMapper.selectById(courseId);
        if (sc == null) {
            throw new BizException("秒杀课程不存在");
        }
        redisTemplate.opsForValue().set(RedisKeys.seckillStock(courseId),
                String.valueOf(sc.getStockCount()));
        log.info("秒杀名额预热完成 courseId={} stock={}", courseId, sc.getStockCount());
    }

    /** 剩余名额查询：给前端倒计时/名额展示用 */
    public long remainStock(Long courseId) {
        String v = redisTemplate.opsForValue().get(RedisKeys.seckillStock(courseId));
        return v == null ? -1 : Long.parseLong(v);
    }

    /** 正在进行中的秒杀活动（给首页"抢课专区"用） */
    public List<SeckillCourse> activeList() {
        return seckillCourseMapper.selectList(null).stream()
                .filter(sc -> sc.getStatus() == 1)
                .toList();
    }

    private String rootMsg(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName()
                : (msg.length() > 80 ? msg.substring(0, 80) : msg);
    }
}
