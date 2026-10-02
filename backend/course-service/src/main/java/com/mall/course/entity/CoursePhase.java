package com.mall.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 选课阶段表：对应 sql/init.sql 的 course_phase 表。
 * type：WISH=志愿填报，MAIN=正选，ADD=补选；status：0=关闭，1=进行中，2=已结束。
 * enroll-service 在选课入口查"当前进行中的阶段"做阶段门控。
 */
@Data
@TableName("course_phase")
public class CoursePhase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createdAt;
}
