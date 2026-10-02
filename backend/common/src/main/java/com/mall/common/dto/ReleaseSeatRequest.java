package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 释放名额：退课时把名额还回 seat 表（available 增加，上限为 total） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseSeatRequest implements Serializable {
    private Long courseId;
    private Integer quantity;
}
