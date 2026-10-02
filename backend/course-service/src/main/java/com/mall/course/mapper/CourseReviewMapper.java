package com.mall.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.course.entity.CourseReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseReviewMapper extends BaseMapper<CourseReview> {

    /** 课程平均分：无评价时返回 null，调用方按 0 处理 */
    @Select("SELECT AVG(score) FROM course_review WHERE course_id = #{courseId}")
    Double avgScore(@Param("courseId") Long courseId);
}
