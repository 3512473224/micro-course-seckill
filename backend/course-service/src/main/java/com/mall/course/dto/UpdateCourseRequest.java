package com.mall.course.dto;

import lombok.Data;

/** 管理端改课：时间/教室/容量/上下架，字段全可选，只传要改的 */
@Data
public class UpdateCourseRequest {
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
    private String weeks;
    private String classroom;
    private Integer capacity;
    /** 1=上架，0=下架 */
    private Integer status;
}
