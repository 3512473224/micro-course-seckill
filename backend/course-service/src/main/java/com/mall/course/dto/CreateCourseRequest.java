package com.mall.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 开课入参：教师端（草稿）与管理端（直接发布）共用，status 由调用方决定 */
@Data
public class CreateCourseRequest {
    @NotBlank(message = "课程名不能为空")
    private String name;
    @NotNull(message = "学分不能为空")
    private BigDecimal credit;
    private String description;
    @NotNull(message = "上课星期不能为空")
    @Min(value = 1, message = "星期取值 1-7")
    @Max(value = 7, message = "星期取值 1-7")
    private Integer weekday;
    @NotNull(message = "起始节次不能为空")
    @Min(value = 1, message = "节次必须 >=1")
    private Integer startSection;
    @NotNull(message = "结束节次不能为空")
    @Min(value = 1, message = "节次必须 >=1")
    private Integer endSection;
    /** 教学周，如 "1-16"；默认 1-16 */
    private String weeks = "1-16";
    private String classroom;
    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量至少为 1")
    private Integer capacity;
}
