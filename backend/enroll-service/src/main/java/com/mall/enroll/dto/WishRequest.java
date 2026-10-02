package com.mall.enroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 填报志愿入参 */
@Data
public class WishRequest {
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;
    /** 1=第一志愿，2=第二志愿 */
    @NotNull(message = "志愿优先级不能为空")
    @Min(value = 1, message = "志愿优先级只能是 1 或 2")
    @Max(value = 2, message = "志愿优先级只能是 1 或 2")
    private Integer priority;
}
