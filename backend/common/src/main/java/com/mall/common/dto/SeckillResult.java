package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 抢课结果：success=false 时前端直接展示 message（如"名额已抢光"） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResult implements Serializable {
    private boolean success;
    private String message;
    /** 抢课成功时返回的选课单 id */
    private Long enrollId;
}
