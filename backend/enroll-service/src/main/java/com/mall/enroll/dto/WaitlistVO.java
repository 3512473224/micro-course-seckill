package com.mall.enroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 我的候补视图：带课程名和排队位置 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistVO {
    private Long id;
    private Long courseId;
    private String courseName;
    private Integer position;
    private LocalDateTime createdAt;
}
