package com.mall.enroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选课单：对应 sql/init.sql 的 enroll_order 表。
 * 生产环境 enroll_order 与 seat 表分属不同库（enroll 库 / seat 库），这正是需要 Seata 的原因。
 */
@Data
@TableName("enroll_order")
public class EnrollOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务单号：时间戳+随机，幂等/对账用 */
    private String orderNo;
    private Long userId;
    private Long courseId;
    private String courseName;
    private BigDecimal credit;
    /** 0=普通选课，1=秒杀抢课 */
    private Integer orderType;
    /** 0=待确认，1=已选上，2=已取消 */
    private Integer status;
    private LocalDateTime createdAt;
}
