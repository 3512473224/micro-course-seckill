package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** course-service -> enroll-service：秒杀抢课成功后，请求创建选课单 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillEnrollRequest implements Serializable {
    private Long userId;
    private Long courseId;
}
