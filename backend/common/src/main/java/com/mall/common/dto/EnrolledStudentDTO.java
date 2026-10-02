package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 已选课学生：enroll-service 提供给 course-service 拼教师端花名册 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrolledStudentDTO implements Serializable {
    private Long userId;
    private String orderNo;
    private LocalDateTime createdAt;
}
