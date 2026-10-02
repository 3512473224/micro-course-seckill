package com.mall.seat.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.mall.common.dto.DeductSeatRequest;
import com.mall.common.dto.ReleaseSeatRequest;
import com.mall.common.dto.SeatDTO;
import com.mall.common.exception.BizException;
import com.mall.seat.config.SeatBlockHandler;
import com.mall.seat.entity.Seat;
import com.mall.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* 名额扣减：Seata AT 的分支事务。
*
* 调用链：enroll-service.createEnroll(@GlobalTransactional)
* -> Feign POST /api/seat/deduct（XID 通过请求头透传）
* -> 本方法执行 update（经 Seata 数据源代理，自动写 undo_log）
*
* 关键点：
* 1. 本方法加 @Transactional——分支事务的提交/回滚由 Seata TC 驱动，
* 本地加上反而可能干扰（Seata 代理层会自己管理连接）；
* 2. 抛出的 BizException 会通过 Feign 传回发起方，触发全局回滚；
* 3. @SentinelResource("deductSeat")：给扣名额加一层限流，防止 DB 被打爆，
* 被限流时走 SeatBlockHandler 快速失败。
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

private final SeatMapper seatMapper;

@SentinelResource(value = "deductSeat",
blockHandlerClass = SeatBlockHandler.class, blockHandler = "deductBlock")
public void deduct(DeductSeatRequest request) {
int rows = seatMapper.deduct(request.getCourseId(), request.getQuantity());
if (rows == 0) {
// 名额不足：抛异常 -> Feign 透传 -> enroll-service 全局事务回滚 -> 选课单被删除
throw new BizException("课程名额不足");
}
log.info("扣减名额成功 courseId={} quantity={}", request.getCourseId(), request.getQuantity());
}

public int remain(Long courseId) {
Seat seat = seatMapper.selectById(courseId);
return seat == null? 0: seat.getAvailable();
}

/**
* 释放名额：退课时调用。注意这个方法不在 Seata 全局事务里——
* 退课链路是"先本地取消订单、再远程释放名额"的顺序执行，
* 失败时抛异常由调用方感知，而不是靠分布式事务回滚。
*/
public void release(ReleaseSeatRequest request) {
int rows = seatMapper.release(request.getCourseId(), request.getQuantity());
if (rows == 0) {
throw new BizException("课程名额记录不存在，释放失败");
}
log.info("释放名额成功 courseId={} quantity={}",
request.getCourseId(), request.getQuantity());
}

/** 名额详情：{courseId,total,available}，候补资格判断与补位前确认用 */
public SeatDTO info(Long courseId) {
Seat seat = seatMapper.selectById(courseId);
if (seat == null) {
return new SeatDTO(courseId, 0, 0);
}
return new SeatDTO(seat.getCourseId(), seat.getTotal(), seat.getAvailable());
}
}
