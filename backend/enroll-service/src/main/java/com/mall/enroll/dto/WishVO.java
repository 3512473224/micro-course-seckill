package com.mall.enroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 我的志愿视图：带课程名和上课时间 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishVO {
    private Long id;
    private Long courseId;
    private String courseName;
    private Integer priority;
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private String classroom;
    private LocalDateTime createdAt;
}
