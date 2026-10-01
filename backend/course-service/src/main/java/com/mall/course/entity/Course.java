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
    /** 1=上架可选，0=下架 */
    private Integer status;
    private LocalDateTime createdAt;
}
