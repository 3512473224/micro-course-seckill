package com.mall.course.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则：代码方式加载（演示用）。生产推荐推模式：规则配在 Nacos，
 * sentinel-datasource-nacos 自动推送，各节点动态生效，不用改代码重启。
 *
 * 规则含义（面试必问"Sentinel 限流算法"）：
 *  - grade=QPS：按每秒请求数限流（另一种是按线程数，适合慢调用防堆积）；
 *  - count=200：单机每秒最多放行 200 个抢课请求，超出直接快速失败；
 *  - controlBehavior=DEFAULT：直接拒绝。其它选项：WarmUp（冷启动预热，防缓存未建时打爆 DB）、
 *    Throttling（排队等待，匀速通过，适合秒杀削峰）。
 */
@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule seckillRule = new FlowRule("seckill");
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckillRule.setCount(200);
        seckillRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(seckillRule);

        FlowRuleManager.loadRules(rules);
    }
}
