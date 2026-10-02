# 学衡教务 · 选课系统（微服务版）

> 秋招简历项目：Java 17 + Spring Cloud Alibaba 微服务 + Vue3 全栈。
> 一个**真实可用的大学教务选课系统**：选课阶段管理、志愿填报与自动录取、时间冲突检测、学分上限、候补排队自动补位、退课、课程评价、教师开课、教务管理后台——外加秒杀抢课（Redis Lua）与 Seata 分布式事务。

## 产品速览

| 角色 | 能做什么 |
|---|---|
| 学生 | 选课广场浏览课程 → 志愿填报 / 正选直选 / 补选 → 我的课表（周视图）→ 退课 → 课程评价；名额满可候补排队，自动补位 |
| 教师 | 开课申请（走审核）→ 查看选课名单 → 导出 CSV 名单 |
| 教务管理员 | 排课 CRUD 与发布/下架 → 选课阶段开关（志愿/正选/补选）→ 志愿结算 → 数据看板（选课率、热门排行） |

测试账号：`student01/123456`（学生）、`teacher01/123456`（教师）、`admin/123456`（管理员）。

## 架构图

```
                        ┌──────────────┐
                        │   Vue3 前端   │  :80 学术风主题（藏青+暖橙）
                        │ 广场/周课表/  │
                        │ 教师/管理后台 │
                        └──────┬───────┘
                               │ /api/**
                        ┌──────▼──────┐
                        │   Gateway   │  :8080
                        │ 统一鉴权+    │  验 Token → 透传 X-User-Id / X-User-Role
                        │ 角色校验     │  /admin/** 必须 admin，/teacher/** 必须 teacher
                        └──────┬──────┘
        ┌──────────────┬───────┴────────┬──────────────┐
        ▼              ▼                ▼              ▼
  user-service   course-service   enroll-service  seat-service
    :8081            :8082             :8083           :8084
  登录/角色签发   课程/阶段/评价     选课业务核心     名额扣减/释放
                教师开课/管理后台   阶段门控/冲突检测  (Seata 分支方)
                (Redis 缓存)      学分上限/志愿/候补
                                @GlobalTransactional
                                (Seata 发起方)
        │              │                │              │
        └──────────────┴────────┬───────┴──────────────┘
                               ▼
        ┌────────┬─────────┬──────────┬──────────┐
        ▼        ▼         ▼          ▼          ▼
      Nacos    MySQL     Redis     Seata      Sentinel
      :8848    :3306     :6379     :8091      (规则代码
    注册+配置   业务表    缓存+秒杀   TC协调     内置/控制台)
```

**核心业务链路**

| 场景 | 链路 |
|---|---|
| 登录 | 前端 → 网关（白名单）→ user-service → Token（含 role） |
| 志愿填报 | 学生填志愿（WISH 阶段）→ 管理员一键结算 → 按"志愿优先级→填报时间"录取，录满转候补 |
| 正选/补选 | 阶段门控 → 时间冲突检测 → 学分上限 → Seata 建单+扣名额 |
| 秒杀抢课 | SETNX 限购 → Lua 原子扣 Redis 名额 → Feign 建单 → 扣 DB 名额 |
| 退课 | 校验阶段窗口 → 订单置退课 → 释放名额 → 候补队列自动补位 |
| 课程评价 | 核验"选上过该课" → 一人一评 → 课程详情页均分展示 |

## 技术栈

| 领域 | 选型 | 版本 |
|---|---|---|
| 语言/框架 | Java 17，Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| 注册/配置中心 | Nacos | 2.3.2 |
| 网关 | Spring Cloud Gateway（WebFlux）+ 角色校验 | - |
| 服务调用 | OpenFeign + LoadBalancer | - |
| 限流熔断 | Sentinel（@SentinelResource + Feign fallback） | - |
| 分布式事务 | Seata AT 模式（`io.seata`，2.0.0） | 2.0.0 |
| 缓存/秒杀 | Redis（Lua 原子脚本） | 7 |
| 数据库 | MySQL | 8 |
| ORM | MyBatis-Plus | 3.5.7 |
| 前端 | Vue3 + Vite + Element Plus（自定义学术风主题）+ Axios | - |
| 编排 | Docker Compose | - |

> 注意：Seata 必须用 `io.seata` 包。`org.apache.seata` 是 2.1.0 才开始的包名；
> Spring Cloud Alibaba 2023.0.1.0 锁的是 Seata 2.0.0，写错包名编译不过。

## 一键启动

```bash
# 1. 建库并导入数据（22 门课程 + 3 个选课阶段 + 测试账号）
mysql -h 127.0.0.1 -u root -p < sql/init.sql
mysql -h 127.0.0.1 -u root -p mall < sql/undo_log.sql   # Seata AT 回滚表（每个库一份）

# 2. 构建并启动全部容器（首次约 3~8 分钟）
docker compose up -d --build

# 3. 打开前端
# Windows PowerShell: start http://localhost
# Nacos 控制台：http://localhost:8848/nacos
# Seata 控制台：http://localhost:7091（seata/seata）

# 停止（保留数据）
docker compose stop
```

> 本地直跑（不走 docker）：先 `docker compose up -d mysql redis nacos seata-server`，
> 再各服务 `mvn spring-boot:run`（NACOS_ADDR 等默认指向 127.0.0.1）。

## 3 分钟演示脚本

```bash
# 0. 学生登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student01","password":"123456"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 1. 看当前选课阶段（正选进行中）
curl -s http://localhost:8080/api/course/phase/current -H "Authorization: Bearer $TOKEN"

# 2. 选一门课（自动做时间冲突+学分上限校验，Seata 分布式事务建单）
curl -s -X POST http://localhost:8080/api/enroll/create \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"courseId":1,"quantity":1}'

# 3. 再选一门时间冲突的课 → 被拒绝并提示"与《XX》课程时间冲突"
curl -s -X POST http://localhost:8080/api/enroll/create \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"courseId":<冲突课程id>,"quantity":1}'

# 4. 我的课表（周视图在前端 /schedule 页）
curl -s http://localhost:8080/api/enroll/my -H "Authorization: Bearer $TOKEN"

# 5. 退课（触发候补自动补位）
curl -s -X POST http://localhost:8080/api/enroll/drop/<orderId> -H "Authorization: Bearer $TOKEN"

# 6. 管理员登录 → 志愿结算（先用 admin/123456 登录拿 ADMIN_TOKEN）
curl -s -X POST "http://localhost:8080/api/enroll/admin/wish/settle?phaseId=1" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## 简历核心亮点

1. **完整教务业务闭环**：选课阶段状态机（志愿→正选→补选）、时间冲突检测、学分上限、候补自动补位——不是 CRUD demo，是真实业务规则。
2. **网关统一鉴权 + RBAC**：JWT 携带 role，网关按路径前缀做角色校验（学生调教师接口直接 403），下游经 `X-User-Id`/`X-User-Role` 透传。
3. **Seata AT 分布式事务**：选课横跨 enroll/seat 两服务；配套 `/create-no-seata` 对比接口，直观展示"有事务 vs 脏数据"；注意 `io.seata` 包名坑（2.0.0）。
4. **Redis + Lua 秒杀防超卖**：Lua 原子扣减 + SETNX 每人限抢 1 + 失败补偿回滚；课程列表 Cache-Aside 缓存。
5. **Nacos + Sentinel 服务治理**：注册发现与配置中心；抢课接口 QPS 限流 + blockHandler 快速失败；Feign fallback 熔断降级。

更多深挖 Q&A 见 [docs/INTERVIEW.md](docs/INTERVIEW.md)，
Nacos 配置中心步骤见 [docs/nacos-config.md](docs/nacos-config.md)。

## 目录结构

```
micro-mall/
├── backend/
│   ├── common/              # Result/异常/JwtUtil(含role)/Lua脚本/Feign配置/跨服务DTO
│   ├── gateway/             # :8080 网关 + 鉴权/角色校验过滤器
│   ├── user-service/        # :8081 登录/角色签发/批量查用户
│   ├── course-service/      # :8082 课程/阶段/评价/教师开课/管理后台
│   ├── enroll-service/      # :8083 选课核心（阶段门控/冲突/学分/志愿/候补/退课）
│   ├── seat-service/        # :8084 名额扣减/释放
│   ├── seata/conf/          # seata-server file 模式配置
│   └── Dockerfile
├── frontend/                # Vue3 学术风主题
│   └── src/views/           # Home(广场)/CourseDetail/Schedule(周视图)/Wish/MyEnroll
│                            # Teacher(教师)/Admin(管理)/Login/Forbidden
├── sql/
│   ├── init.sql             # 全量建表 + 22 门课程 + 3 阶段 + 测试账号
│   └── undo_log.sql         # Seata AT 回滚表
├── docs/
│   ├── INTERVIEW.md         # 简历描述 + 10 道深挖 Q&A + 演示话术
│   └── nacos-config.md
├── docker-compose.yml
└── README.md
```
