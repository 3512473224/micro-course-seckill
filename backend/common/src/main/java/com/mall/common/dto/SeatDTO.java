package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 名额视图：seat-service 提供，候补/退课补位逻辑用它判断是否还有名额 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO implements Serializable {
    private Long courseId;
    private Integer total;
    private Integer available;
}
