package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 选课阶段视图（course-service 维护，enroll-service 通过 Feign 读取做阶段门控）。
 * type：WISH=志愿填报，MAIN=正选，ADD=补选。
 * status：0=关闭，1=进行中，2=已结束。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursePhaseDTO implements Serializable {
    private Long id;
    private String name;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
