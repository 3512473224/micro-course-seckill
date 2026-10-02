package com.mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 用户简要信息：user-service 的 batch 接口返回，供教师端花名册展示姓名用 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefDTO implements Serializable {
    private String username;
    private String nickname;
}
