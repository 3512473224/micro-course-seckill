package com.mall.common.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 秒杀 Lua 脚本：为什么 Lua 能防超卖？
 *
 * 1. Redis 是单线程执行命令的，整个 Lua 脚本在执行期间不会被其它客户端打断；
 * 2. "查名额 -> 判断 -> 扣减" 三步如果用三条普通 Redis 命令，高并发下两个请求可能同时
 *    读到 stock=1 然后都扣减成功 -> 超卖。而 Lua 把"判断+扣减"打包成一个原子操作，
 *    同一时刻只有一个脚本在跑，第二个请求看到的已经是扣减后的值；
 * 3. 对比方案：Redis 分布式锁（Redisson）也能做，但多一次加锁/解锁网络往返，
 *    吞吐量不如 Lua；数据库乐观锁（version）适合低并发，秒杀场景下大量重试会打爆 DB。
 *
 * 脚本语义：KEYS[1]=名额key，ARGV[1]=扣减数量；返回 1=扣减成功，0=名额不足。
 */
public final class LuaScripts {

    private LuaScripts() {
    }

    public static final String SECKILL_DEDUCT =
            "local stock = tonumber(redis.call('GET', KEYS[1]) or '0')\n"
                    + "if stock < tonumber(ARGV[1]) then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "redis.call('DECRBY', KEYS[1], ARGV[1])\n"
                    + "return 1";

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT =
            new DefaultRedisScript<>(SECKILL_DEDUCT, Long.class);
}
