package com.mall.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.seat.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 扣减名额：单条原子 UPDATE。
 *
 * 为什么 WHERE available >= #{quantity} 能防超卖？
 * MySQL 单条语句是原子的：并发下两个事务同时执行，只有 available 足够的那个能匹配到行
 * 并拿到行锁，另一个要么等待、要么匹配 0 行。返回影响行数 == 0 即视为名额不足。
 * 这是 DB 层面的最后一道防线（秒杀主防线在 Redis Lua，见 course-service）。
 */
@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    @Update("UPDATE seat SET available = available - #{quantity}, frozen = frozen + #{quantity} "
            + "WHERE course_id = #{courseId} AND available >= #{quantity}")
    int deduct(@Param("courseId") Long courseId, @Param("quantity") int quantity);

    /**
     * 释放名额：退课时把名额还回来。
     * LEAST(total, ...) 保证 available 永远不超过 total，防止重复释放刷出"幽灵名额"。
     */
    @Update("UPDATE seat SET available = LEAST(total, available + #{quantity}), "
            + "frozen = GREATEST(0, frozen - #{quantity}) "
            + "WHERE course_id = #{courseId}")
    int release(@Param("courseId") Long courseId, @Param("quantity") int quantity);
}
