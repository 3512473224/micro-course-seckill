package com.mall.enroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加入候补队列入参 */
@Data
public class WaitlistRequest {
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;
}
