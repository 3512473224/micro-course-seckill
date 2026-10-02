package com.mall.enroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿填报：对应 sql/init.sql 的 enroll_wish 表。
 * 志愿填报阶段（WISH）学生先填志愿，阶段结束后管理员统一结算录取；
 * UK(student_id, course_id) 保证同一课程不重复填报。
 */
@Data
@TableName("enroll_wish")
public class EnrollWish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long courseId;
    /** 1=第一志愿，2=第二志愿 */
    private Integer priority;
    private LocalDateTime createdAt;
}
