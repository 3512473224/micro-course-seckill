package com.mall.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理端数据看板条目：按选上人数倒序 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardItemVO {
    private Long courseId;
    private String name;
    private Integer total;
    private Integer enrolled;
    /** 选课率 = enrolled / total */
    private Double rate;
    private Double avgScore;
}
