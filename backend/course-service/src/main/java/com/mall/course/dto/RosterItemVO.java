package com.mall.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 花名册条目：选课学生信息 + 选课单号/时间 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RosterItemVO {
    private Long userId;
    private String username;
    private String nickname;
    private String orderNo;
    private LocalDateTime createdAt;
}
