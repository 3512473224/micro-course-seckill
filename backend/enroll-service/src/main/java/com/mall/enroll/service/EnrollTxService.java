package com.mall.enroll.service;

import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.enroll.entity.EnrollOrder;
import com.mall.enroll.feign.SeatFeignClient;
import com.mall.enroll.mapper.EnrollOrderMapper;
import io.seata.spring.annotation.GlobalTransactional;
// 注意：必须用 io.seata（不是 org.apache.seata）。
// 包名 org.apache.seata 是从 Seata 2.1.0 才开始的；而 Spring Cloud Alibaba 2023.0.1.0
// 管理的 seata.version=2.0.0，坐标仍是 io.seata。写错会导致编译期类缺失。
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seata AT 分布式事务的入口 Bean。
 *
 * 为什么把 @GlobalTransactional 的方法单独放在这个 Bean 里？
 * Spring 的事务注解靠代理生效，同一个类内部方法自调用不会走代理，
 * 注解就失效了。EnrollService.createEnroll 需要先做阶段/冲突/学分等纯校验
 * （这些校验不进事务，避免白白占用全局锁），校验通过后再调这里的事务方法，
 * 所以事务入口必须是一个独立 Bean。
 *
 * Seata AT 两阶段原理（为什么这里需要它）：
 * 选课单（enroll 库）与名额（seat 库）在不同库，一次选课要同时写两个库。
 * 一阶段：@GlobalTransactional 生成全局 XID（经 Feign 头透传给 seat-service），
 * 各分支本地 SQL 经 Seata 数据源代理执行，写入 undo_log 后各自提交本地事务；
 * 二阶段：全成功则 TC 通知各分支异步删除 undo_log；任一分支失败则用 undo_log
 * 的前镜像反向补偿（insert→delete），保证"要么都成功，要么都回滚"。
 * AT 用全局锁防一阶段脏写：涉及的写操作必须都走 Seata 分支事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollTxService {

    private final EnrollOrderMapper enrollOrderMapper;
    private final SeatFeignClient seatFeignClient;

    /**
     * 建单 + 扣名额：Seata AT 全局事务。
     * orderType：0=普通选课，1=秒杀抢课，2=志愿录取，3=候补转正。
     */
    @GlobalTransactional(name = "enroll-create-tx", rollbackFor = Exception.class, timeoutMills = 30000)
    public Long createInTx(Long userId, CourseDTO course, int orderType, int quantity) {
        EnrollOrder order = buildOrder(userId, course, orderType);
        enrollOrderMapper.insert(order);
        log.info("本地选课单已创建 id={} orderType={} xid={}",
                order.getId(), orderType, io.seata.core.context.RootContext.getXID());

        // 远程扣名额：名额不足时 seat-service 侧抛 BizException，经 Feign 传回这里
        // 触发全局回滚，上面 insert 的选课单会被 undo_log 反向删除
        deductSeat(course.getId(), quantity);
        return order.getId();
    }

    private void deductSeat(Long courseId, int quantity) {
        Result<Void> deductResult = seatFeignClient.deduct(
                new DeductSeatRequest(courseId, quantity));
        if (deductResult == null || deductResult.getCode() != 200) {
            throw new BizException("扣减名额失败：" + (deductResult == null ? "无响应" : deductResult.getMsg()));
        }
    }

    public static EnrollOrder buildOrder(Long userId, CourseDTO course, int orderType) {
        EnrollOrder order = new EnrollOrder();
        order.setOrderNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 9999));
        order.setUserId(userId);
        order.setCourseId(course.getId());
        order.setCourseName(course.getName());
        order.setCredit(course.getCredit() == null ? new BigDecimal("2.0") : course.getCredit());
        order.setOrderType(orderType);
        order.setStatus(1); // 演示直接"已选上"；生产：待支付/待审核
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
