package com.mall.enroll.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEnrollRequest {
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;
    /** 普通选课一般一次选 1 门，保留 quantity 字段演示"批量"语义 */
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity = 1;
}
