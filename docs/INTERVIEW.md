# 面试手册：学衡教务选课系统（微服务版）

## 一、简历描述（可直接抄）

> **学衡教务选课系统（微服务版）**｜Spring Cloud Alibaba + Vue3｜个人项目
> - 还原真实大学教务选课全流程：选课阶段状态机（志愿填报→正选→补选）、志愿自动录取、时间冲突检测、学分上限、候补排队自动补位、退课、课程评价、教师开课、教务管理后台
> - 网关统一鉴权 + RBAC：JWT 携带 role，网关按路径前缀做角色校验（/admin/**、/teacher/**），下游经 X-User-Id / X-User-Role 透传
> - 分布式事务：选课链路 Seata AT（`io.seata` 2.0.0，注意 2.1.0 才改名为 org.apache.seata），建单 + 远程扣名额要么都成功要么都回滚；配套无事务对比接口
> - 高并发抢课：Redis 预扣名额 + Lua 脚本原子扣减防超卖，SETNX 每人限抢 1；Sentinel 限流 + Feign fallback 熔断降级
> - Nacos 注册中心 + 配置中心；课程列表 Redis 缓存；Docker Compose 一键编排

## 二、深挖 Q&A（10 个）

### Q1：选课阶段状态机是怎么设计的？各阶段规则有什么不同？
A：`course_phase` 表：type 分 WISH（志愿填报）/ MAIN（正选）/ ADD（补选），status 0 关闭 / 1 进行中 / 2 已结束。
规则差异：WISH 阶段只能填志愿不能直选（`createEnroll` 校验阶段类型，WISH 直接拒绝并提示去填志愿）；
MAIN 阶段先到先得，秒杀课程走 Lua 链路；ADD 阶段只开放剩余名额直选。
当前阶段由 `currentPhase()` 判定：status=1 且 now 落在 [start_time, end_time] 内。
代码位置：`course-service/.../service/CourseService.java`（阶段查询）、`enroll-service/.../service/EnrollService.java`（阶段门控在事务之外先做）。

### Q2：时间冲突检测怎么做？教学周 "1-8,10-16" 这种多段格式怎么比？
A：选课时查该生已选上（status=1）的全部订单，一次 batch 拉取课程时间，逐门比对：
weekday 相同 且 节次区间重叠（[s1,e1] 与 [s2,e2] 相交）且 教学周重叠 → 冲突，报错"与《XX》课程时间冲突（周X 第A-B节）"。
教学周解析在 `common/.../util/WeeksUtil.java`：纯函数，把 "1-8,10-16" 解析成多个 [start,end] 段，两两区间相交即重叠。
为省 Feign 往返，课程时间用 `POST /api/course/batch` 一次拉全，而不是每门调一次。

### Q3：Seata 用的是哪个包？为什么不是 org.apache.seata？
A：`io.seata`。这是个真实踩坑点：包名 `org.apache.seata` 是从 Seata **2.1.0** 才开始的；
而 Spring Cloud Alibaba 2023.0.1.0 的 BOM 里 `seata.version=2.0.0`，groupId 仍是 `io.seata`。
写成 `org.apache.seata.spring.annotation.GlobalTransactional` 会编译期直接类缺失。
代码位置：`enroll-service/.../service/EnrollService.java` import 处有注释说明。

### Q4：@GlobalTransactional 的方法为什么抽到了 EnrollTxService 独立 Bean？
A：Spring 的 @Transactional / @GlobalTransactional 靠代理生效，**同类内自调用不走代理，注解会静默失效**。
`EnrollService.createEnroll` 先做纯校验（阶段门控、课程有效、去重、时间冲突、学分上限），
这些校验不进事务——避免无效占用 Seata 全局锁；校验通过后再调 `EnrollTxService` 独立 Bean 的事务方法。
这是 @Transactional 家族最经典的坑，单独抽 Bean 是标准解法。

### Q5：志愿结算是怎么做的？两个人同时被录取会超发名额吗？
A：`POST /api/enroll/admin/wish/settle?phaseId=`：按课程分组志愿，组内按 (priority ASC, createdAt ASC) 排序，
逐个用 `@GlobalTransactional` 做"建单 + Feign 扣名额"；名额不足抛异常时该生转入 waitlist（position 递增），继续下一个人。
不会超发：名额扣减最终落在 `seat-service` 的 `available` 上，SQL 是 `available = available - ? WHERE available >= ?`，
DB 层面兜底；且结算由管理员手动触发，单线程执行，无并发录取。
代码位置：`enroll-service/.../service/EnrollService.java#settleWishes`。

### Q6：退课后候补是怎么自动补位的？极端情况（补位时又没名额了）怎么办？
A：`drop` 流程：订单置 status=2 → 调 `seat/release` 释放名额（`LEAST(total, …)` 防刷出幽灵名额）
→ 取该课程 waitlist 中 position 最小且 status=0 的人 → 确认还有名额 → `createInTx(orderType=3)` 建单 + 扣名额 + waitlist 置 1。
极端情况：如果补位建单失败（并发下名额又被抢走），catch 住只打日志，**不影响退课本身成功**——
退课是用户主操作必须成功，补位是尽力而为的后台逻辑，两者解耦。
代码位置：`enroll-service/.../service/EnrollService.java#drop`。

### Q7：Lua 脚本为什么能防超卖？和 Redis 分布式锁比好在哪？
A：Redis 单线程执行命令，整个 Lua 脚本执行期间不会被其他客户端打断；
"查名额→判断→扣减"三步打包成一个原子操作，第二个请求看到的永远是扣减后的值。
对比：Redisson 分布式锁多一次加锁/解锁的网络往返，吞吐不如 Lua；
DB 乐观锁（version）在秒杀场景下大量重试会打爆 DB。
脚本语义：KEYS[1]=名额 key，ARGV[1]=扣减数，返回 1 成功 / 0 名额不足。
代码位置：`common/.../constant/LuaScripts.java`。

### Q8：网关的角色校验是怎么做的？为什么不在每个服务里做？
A：`GlobalAuthFilter`（order=-100，在转发前执行）：白名单放行登录 → 验 Token 签名+过期 →
透传 `X-User-Id` / `X-User-Role` → 按路径前缀校验角色（`/api/course/admin`、`/api/enroll/admin` 必须 admin；
`/api/course/teacher` 必须 teacher/admin），不满足返回 403。
收口在网关做：鉴权逻辑只写一处，不会出现"某个服务漏验"；下游专注业务，直接信任透传头。
代价是网关成单点，生产做多实例 + 前挂 LB。
代码位置：`gateway/.../filter/GlobalAuthFilter.java`。

### Q9：课程评价怎么防止刷分？（只能选上过的人评是怎么实现的？）
A：三层防护：① `POST /api/course/review` 调 enroll-service 的 `/check?studentId=&courseId=`，
只有 status=1 的选课单才放行，否则 403；② `course_review` 表 UK(student_id, course_id)，一人一课只能评一次，
并发重复提交 DB 直接拦；③ score 限制 1~5，comment 限长。
均分用 SQL `AVG(score)` 实时算，数据量小不需要缓存。

### Q10：如果让你继续做，这个系统下一步最该补什么？
A：三件事，按优先级：① 选课结果的**消息通知**（候补补位成功、志愿录取结果用站内信/短信通知，现在是被动查）；
② **分库分表**：现在演示是一库多表，生产按服务垂直分库，enroll_order 按 student_id 做水平拆分；
③ 秒杀链路的**异步化**：现在秒杀是 Feign 同步建单，高并发下 enroll-service 会成为瓶颈，
生产用 RocketMQ 削峰（先收单→MQ→异步落库），这也是真实大厂秒杀的做法。

## 三、3 分钟演示话术

1. （30s）首页：介绍"这是一个还原真实教务流程的选课系统"，指一下阶段横幅和倒计时。
2. （60s）学生视角：正选阶段选一门课 → 故意选一门时间冲突的课，展示"与《XX》时间冲突"的拒绝 → 我的课表看周视图。
3. （60s）候补+退课：一门满员课点候补 → 换号退课 → 回来看候补自动转正。
4. （30s）管理视角：切 admin，看数据看板（选课率排行），演示志愿结算。
技术点穿插讲：网关 RBAC、Seata 的 io.seata 包名坑、Lua 防超卖。
