package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** enroll-service -> seat-service：扣减课程剩余名额 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductSeatRequest implements Serializable {
    private Long courseId;
    private Integer quantity;
}
