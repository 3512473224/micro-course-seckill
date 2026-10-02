package com.mall.enroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.enroll.entity.EnrollOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EnrollOrderMapper extends BaseMapper<EnrollOrder> {

    /**
     * 各课程已选人数：给 course-service 管理端看板用，一次查全量，避免逐门查。
     * 只统计 status=1（已选上），退课（status=2）的不算。
     */
    @Select("SELECT course_id AS courseId, COUNT(*) AS cnt FROM enroll_order "
            + "WHERE status = 1 GROUP BY course_id")
    List<Map<String, Object>> countByCourse();
}
