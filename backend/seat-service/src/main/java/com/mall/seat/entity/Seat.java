package com.mall.seat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程名额表：对应 sql/init.sql 的 seat 表。
 * available=剩余可抢名额，frozen=已占未确认（演示简化，实际选课直接扣减）。
 * version 字段预留给乐观锁方案（演示用 WHERE available >= quantity 的原子更新，见 Mapper）。
 */
@Data
@TableName("seat")
public class Seat {
    @TableId
    private Long courseId;
    private Integer total;
    private Integer available;
    private Integer frozen;
    private Integer version;
}
