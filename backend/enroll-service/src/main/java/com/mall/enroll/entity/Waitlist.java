package com.mall.enroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候补队列：对应 sql/init.sql 的 waitlist 表。
 * 课程名额满时学生排队；有人退课释放名额后按 position 顺序自动补位。
 * status：0=排队中，1=已补位，2=已取消。
 */
@Data
@TableName("waitlist")
public class Waitlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long courseId;
    /** 队列位置：课程内递增，越小越先补位 */
    private Integer position;
    private Integer status;
    private LocalDateTime createdAt;
}
