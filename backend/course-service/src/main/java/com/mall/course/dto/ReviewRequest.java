package com.mall.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 课程评价入参 */
@Data
public class ReviewRequest {
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1-5 分")
    @Max(value = 5, message = "评分 1-5 分")
    private Integer score;
    private String comment;
}
