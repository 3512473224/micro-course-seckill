package com.mall.course.dto;

import com.mall.course.entity.CourseReview;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 课程评价汇总：平均分 + 评价数 + 明细 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryVO {
    private Double avgScore;
    private int count;
    private List<CourseReview> list;
}
