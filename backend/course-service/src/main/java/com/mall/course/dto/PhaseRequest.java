package com.mall.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 阶段管理入参：新增/修改共用 */
@Data
public class PhaseRequest {
    @NotBlank(message = "阶段名称不能为空")
    private String name;
    /** WISH=志愿填报，MAIN=正选，ADD=补选 */
    @NotBlank(message = "阶段类型不能为空")
    private String type;
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
    /** 0=关闭，1=进行中，2=已结束 */
    private Integer status;
}
