package com.mall.course.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀课程表：哪些课程参与"整点抢课"，以及活动时间窗、放出的名额数。
 * Redis 里的 seckill:stock:{courseId} 由 /api/seckill/init/{courseId} 从 stockCount 预热。
 */
@Data
@TableName("seckill_course")
public class SeckillCourse {
    @TableId
    private Long courseId;
    private Integer stockCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1=进行中，0=未开始/已结束 */
    private Integer status;
}
