package com.mall.course.mapper;

import com.mall.common.dto.SeatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * seat 表的读写（演示环境一库多表，course-service 直连同一 mall 库可读到 seat 表）。
 * 生产按服务垂直分库后，这里应改为调 seat-service 的 Feign 接口。
 */
@Mapper
public interface SeatManageMapper {

    @Select("SELECT course_id AS courseId, total, available FROM seat WHERE course_id = #{courseId}")
    SeatDTO selectSeat(@Param("courseId") Long courseId);

    /** 开课时初始化名额：total=available=capacity */
    @Update("INSERT INTO seat (course_id, total, available) VALUES (#{courseId}, #{total}, #{available})")
    int insertSeat(@Param("courseId") Long courseId,
                   @Param("total") int total,
                   @Param("available") int available);

    /** 调整容量：total 与 available 一起改（available 的重算逻辑在 Service 层） */
    @Update("UPDATE seat SET total = #{total}, available = #{available} WHERE course_id = #{courseId}")
    int updateCapacity(@Param("courseId") Long courseId,
                       @Param("total") int total,
                       @Param("available") int available);
}
