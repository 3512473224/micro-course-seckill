package com.mall.common.constant;

/**
 * Redis key 命名规范：业务:资源:标识。
 * 统一收口，避免各服务拼 key 时写错导致缓存/秒杀逻辑失效。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 秒杀名额（Redis 预扣减，防超卖的核心）：seckill:stock:{courseId} */
    public static String seckillStock(long courseId) {
        return "seckill:stock:" + courseId;
    }

    /** 抢课限购标记（SETNX，每人限抢 1 个名额）：seckill:limit:{courseId}:{userId} */
    public static String seckillLimit(long courseId, long userId) {
        return "seckill:limit:" + courseId + ":" + userId;
    }

    /** 课程列表缓存 */
    public static String courseList() {
        return "course:list";
    }

    /** 课程详情缓存：course:{id} */
    public static String course(long courseId) {
        return "course:" + courseId;
    }
}
