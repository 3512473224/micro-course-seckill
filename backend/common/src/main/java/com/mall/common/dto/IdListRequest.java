package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 批量查询入参：{ids:[1,2,3]}，用于 course-service 的批量查课程、user-service 的批量查用户 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdListRequest implements Serializable {
    private List<Long> ids;
}
