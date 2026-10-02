package com.mall.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 课程表：对应 sql/init.sql 的 course 表 */
@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String teacher;
    private BigDecimal credit;
    private String description;
    private String image;
    /** 1=上架可选，0=下架（教师新开课程先为草稿 0，管理员发布后为 1） */
    private Integer status;
    /** 上课时间：周几（1-7） */
    private Integer weekday;
    /** 起始节次 */
    private Integer startSection;
    /** 结束节次 */
    private Integer endSection;
    /** 教学周，如 "1-16"、"1-8,10-16" */
    private String weeks;
    /** 上课教室，如 "教3-201" */
    private String classroom;
    /** 授课教师的用户 id（教师开课时写入，用于"我的课程"与花名册鉴权） */
    private Long teacherId;
    private LocalDateTime createdAt;
}
