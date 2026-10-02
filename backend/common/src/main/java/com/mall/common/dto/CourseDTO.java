package com.mall.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 跨服务传输的课程视图（enroll-service 通过 Feign 从 course-service 获取）。
 * 只传选课需要的字段，不直接暴露 entity，避免服务间耦合表结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseDTO implements Serializable {
    private Long id;
    private String name;
    private String teacher;
    private BigDecimal credit;
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
    /** 授课教师的用户 id（教师账号登录后开课时写入） */
    private Long teacherId;
}
