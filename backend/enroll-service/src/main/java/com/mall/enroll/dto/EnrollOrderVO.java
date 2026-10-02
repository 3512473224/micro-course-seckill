package com.mall.enroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 我的选课单视图：订单字段 + 课程上课时间/教室/教师名，前端课程表直接渲染 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollOrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long courseId;
    private String courseName;
    private BigDecimal credit;
    private Integer orderType;
    private Integer status;
    private LocalDateTime createdAt;
    /** 上课时间：周几（1-7） */
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private String classroom;
    private String teacherName;
}
