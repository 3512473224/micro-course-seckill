package com.mall.enroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.dto.SeckillEnrollRequest;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.enroll.dto.CreateEnrollRequest;
import com.mall.enroll.entity.EnrollOrder;
import com.mall.enroll.feign.CourseFeignClient;
import com.mall.enroll.feign.SeatFeignClient;
import com.mall.enroll.mapper.EnrollOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.seata.spring.annotation.GlobalTransactional;
// 注意：必须用 io.seata（不是 org.apache.seata）。
// 包名 org.apache.seata 是从 Seata 2.1.0 才开始的；而 Spring Cloud Alibaba 2023.0.1.0
// 管理的 seata.version=2.0.0，坐标仍是 io.seata。写错会导致编译期类缺失。
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
* 选课服务：Seata AT 分布式事务演示。
*
* === Seata AT 模式两阶段原理（面试背诵版）===
* 前提：参与的每个库都有 undo_log 表；每个服务的数据源被 Seata 代理（DataSourceProxy）。
*
* 一阶段（执行 + 预留回滚数据）：
* 1. @GlobalTransactional 开启全局事务，生成全局 XID，通过 Feign 头透传给 seat-service；
* 2. enroll-service 本地 insert 选课单：SQL 经代理执行前，先查出"修改前镜像"，
* 执行后再查"修改后镜像"，连同 SQL 一起写入本库 undo_log，然后提交；
* 3. Feign 调 seat-service 扣名额：同样记录 undo_log 后提交本地事务。
* 注意：一阶段各分支的本地事务是各自提交的，不是"一起提交"！
*
* 二阶段（按一阶段结果二选一）：
* - 全成功：TC（seata-server）通知各分支异步删除 undo_log（很快，性能影响小）；
* - 任一分支失败/超时：TC 通知各分支用 undo_log 的"前镜像"反向补偿回滚
* （insert -> delete，update -> 按前镜像还原），保证"要么都成功，要么都回滚"。
*
* 脏写问题：AT 一阶段用"全局锁"防脏写——分支本地事务提交前会持有记录的全局锁，
* 其他全局事务想改同一条记录必须等锁释放；但本地（非 Seata）事务不受限，所以要求
* 涉及的写操作都走 Seata 分支事务。
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollService {

private final EnrollOrderMapper enrollOrderMapper;
private final CourseFeignClient courseFeignClient;
private final SeatFeignClient seatFeignClient;

/**
* 普通选课：Seata 分布式事务版本。
* 本地建选课单 + 远程扣名额，任何一步失败，全局回滚（选课单不会残留）。
*/
@GlobalTransactional(name = "enroll-create-tx", rollbackFor = Exception.class, timeoutMills = 30000)
public Long createEnroll(Long userId, CreateEnrollRequest req) {
CourseDTO course = getAvailableCourse(req.getCourseId());

EnrollOrder order = buildOrder(userId, course, 0);
enrollOrderMapper.insert(order);
log.info("本地选课单已创建 id={} xid={}",
order.getId(), io.seata.core.context.RootContext.getXID());

// 远程扣名额：名额不足时 seat-service 抛 BizException -> Feign 抛异常 ->
// 触发全局回滚，上面 insert 的选课单会被 undo_log 反向删除
Result<Void> deductResult = seatFeignClient.deduct(
new DeductSeatRequest(req.getCourseId(), req.getQuantity()));
if (deductResult == null || deductResult.getCode()!= 200) {
throw new BizException("扣减名额失败：" + (deductResult == null? "无响应": deductResult.getMsg()));
}
return order.getId();
}

/**
* 对比接口：@GlobalTransactional。
* 用途：演示"不用分布式事务会怎样"——如果 seat-service 扣名额失败，
* 本地 insert 已经提交，选课单残留成脏数据。面试现场演示效果极佳。
*/
public Long createEnrollNoSeata(Long userId, CreateEnrollRequest req) {
CourseDTO course = getAvailableCourse(req.getCourseId());

EnrollOrder order = buildOrder(userId, course, 0);
enrollOrderMapper.insert(order); // 无事务包裹，立即提交，失败也回滚不了
log.warn("选课单已直接提交 id={}", order.getId());

Result<Void> deductResult = seatFeignClient.deduct(
new DeductSeatRequest(req.getCourseId(), req.getQuantity()));
if (deductResult == null || deductResult.getCode()!= 200) {
// 注意：这里即使抛异常，选课单也已经入库 -> 脏数据，Seata 版本不会这样
throw new BizException("扣减名额失败（注意：选课单已残留，未回滚）: "
+ (deductResult == null? "无响应": deductResult.getMsg()));
}
return order.getId();
}

/**
* 秒杀抢课的建单：course-service 的 Lua 预扣成功后调这里。
* 为什么不用 Seata？秒杀是高并发写，Seata 的全局锁 + undo_log + 两阶段提交
* 在高并发下是性能杀手；秒杀链路用 Redis 预扣 + 失败补偿（见 SeckillService），
* 这里只做本服务本地事务 + 同步调 seat 扣 DB 名额（DB 名额做最终兜底）。
*/
@Transactional(rollbackFor = Exception.class)
public Long createSeckillEnroll(SeckillEnrollRequest req) {
CourseDTO course = getAvailableCourse(req.getCourseId());
EnrollOrder order = buildOrder(req.getUserId(), course, 1);
enrollOrderMapper.insert(order);
Result<Void> deductResult = seatFeignClient.deduct(
new DeductSeatRequest(req.getCourseId(), 1));
if (deductResult == null || deductResult.getCode()!= 200) {
throw new BizException("扣减名额失败：" + (deductResult == null? "无响应": deductResult.getMsg()));
}
return order.getId();
}

public List<EnrollOrder> myOrders(Long userId) {
return enrollOrderMapper.selectList(
new LambdaQueryWrapper<EnrollOrder>()
.eq(EnrollOrder::getUserId, userId)
.orderByDesc(EnrollOrder::getId));
}

/** 课程必须存在且上架，否则不允许建单 */
private CourseDTO getAvailableCourse(Long courseId) {
Result<CourseDTO> result = courseFeignClient.getById(courseId);
if (result == null || result.getCode()!= 200 || result.getData() == null) {
throw new BizException("课程不存在");
}
CourseDTO course = result.getData();
if (course.getStatus() == null || course.getStatus()!= 1) {
throw new BizException("课程已下架，不可选");
}
return course;
}

private EnrollOrder buildOrder(Long userId, CourseDTO course, int orderType) {
EnrollOrder order = new EnrollOrder();
order.setOrderNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
+ ThreadLocalRandom.current().nextInt(1000, 9999));
order.setUserId(userId);
order.setCourseId(course.getId());
order.setCourseName(course.getName());
order.setCredit(course.getCredit());
order.setOrderType(orderType);
order.setStatus(1); // 演示直接"已选上"；生产：待支付/待审核
order.setCreatedAt(LocalDateTime.now());
return order;
}
}
