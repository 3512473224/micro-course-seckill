package com.mall.enroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.enroll.entity.Waitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WaitlistMapper extends BaseMapper<Waitlist> {

    /** 某课程当前最大排队位置：新加入的 position = max + 1，保证单调递增 */
    @Select("SELECT COALESCE(MAX(position), 0) FROM waitlist WHERE course_id = #{courseId}")
    int maxPosition(@Param("courseId") Long courseId);
}
