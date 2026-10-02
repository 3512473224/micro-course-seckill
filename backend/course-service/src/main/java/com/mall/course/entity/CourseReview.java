package com.mall.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程评价表：对应 sql/init.sql 的 course_review 表。
 * 只有真正选上过该课的学生才能评价（提交时调 enroll-service 的 check 接口核验），
 * 同一学生一门课只能评一次，由 UK(student_id, course_id) 兜底。
 */
@Data
@TableName("course_review")
public class CourseReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long courseId;
    /** 1-5 分 */
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
