# 校园抢课系统（微服务高并发版）

> 秋招简历项目：Java 17 + Spring Cloud Alibaba 微服务 + Vue3 全栈，
> 以"学生抢课"为业务场景，演示高并发秒杀、分布式事务、服务治理等面试高频点。

## 架构图

```
                        ┌─────────────┐
                        │  Vue3 前端   │  :80 (nginx)
                        │ 选课/抢课/课表│
                        └──────┬──────┘
                               │ /api/**
                        ┌──────▼──────┐
                        │   Gateway   │  :8080
                        │ 统一鉴权过滤器 │  白名单 /api/user/login，其余验 Token
                        │ 路由 lb://   │  验签通过 -> X-User-Id 透传下游
                        └──────┬──────┘
        ┌──────────────┬───────┴────────┬──────────────┐
        ▼              ▼                ▼              ▼
  user-service   course-service   enroll-service  seat-service
    :8081            :8082             :8083           :8084
  登录签发Token   课程列表/详情      普通选课        扣减名额
  Nacos配置刷新   (Redis缓存)      @GlobalTransactional  @SentinelResource
  演示            秒杀抢课:         (Seata AT 发起方)   (Seata AT 分支方)
                  SETNX限购+
                  Lua原子扣减
        │              │                │              │
        └──────────────┴────────┬───────┴──────────────┘
                               ▼
        ┌────────┬─────────┬──────────┬──────────┐
        ▼        ▼         ▼          ▼          ▼
      Nacos    MySQL     Redis     Seata      Sentinel
      :8848    :3306     :6379     :8091      (规则代码
    注册+配置   一库多表   缓存+秒杀   TC协调      内置/控制台)
```

**调用关系速览**

| 场景 | 链路 |
|---|---|
| 登录 | 前端 → 网关(白名单放行) → user-service → 返回 Token |
| 课程列表 | 前端 → 网关(验 Token) → course-service → Redis 缓存 → MySQL |
| 秒杀抢课 | course-service：SETNX 限购 → Lua 原子扣 Redis 名额 → Feign → enroll-service 建单 → Feign → seat-service 扣 DB 名额 |
| 普通选课 | enroll-service `@GlobalTransactional`：本地建单 + Feign 扣名额，失败全局回滚 |

## 技术栈

| 领域 | 选型 | 版本 |
|---|---|---|
| 语言/框架 | Java 17，Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| 注册/配置中心 | Nacos | 2.3.2 |
| 网关 | Spring Cloud Gateway（WebFlux） | - |
| 服务调用 | OpenFeign | - |
| 限流熔断 | Sentinel（@SentinelResource + Feign fallback） | - |
| 分布式事务 | Seata AT 模式 | 2.7.0 |
| 缓存/秒杀 | Redis（Lua 原子脚本） | 7 |
| 数据库 | MySQL | 8 |
| ORM | MyBatis-Plus | 3.5.7 |
| 前端 | Vue3 + Vite + Element Plus + Axios | - |
| 编排 | Docker Compose | - |

## 一键启动

```bash
cd micro-course-seckill

# 1. 构建并启动全部 10 个容器（首次构建约 3~8 分钟，下载依赖+打包）
docker compose up -d --build

# 2. 查看服务启动情况（所有 java 服务日志出现 Started 即就绪）
docker compose ps
docker compose logs -f course-service

# 3. 打开前端
start http://localhost       # Windows PowerShell；macOS 用 open http://localhost
# Nacos 控制台：http://localhost:8848/nacos
# Seata 控制台：http://localhost:7091（账号密码见 seata 配置）

# 停止（保留数据）
docker compose stop
# 彻底删除（含 mysql 数据卷，慎用）
docker compose down -v
```

> 本地直跑（不走 docker）：先 `docker compose up -d mysql redis nacos seata-server`，
> 再各服务 `mvn spring-boot:run`（环境变量 NACOS_ADDR 等默认指向 127.0.0.1，可直连）。

## 抢课演示三步走

```bash
# 0. 登录拿 token（测试账号 student01/123456）
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student01","password":"123456"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo $TOKEN

# 1. 预热秒杀名额：把 DB 里 100 个名额加载到 Redis（开抢前必做）
curl -s -X POST http://localhost:8080/api/seckill/init/1001 \
  -H "Authorization: Bearer $TOKEN"

# 2. 抢课（每人限 1 个名额）
curl -s -X POST http://localhost:8080/api/seckill/1001 \
  -H "Authorization: Bearer $TOKEN"

# 3. 查剩余名额
curl -s http://localhost:8080/api/seckill/stock/1001 \
  -H "Authorization: Bearer $TOKEN"
```

## 秒杀压测：120 并发证明无超卖

```bash
#!/bin/bash
# stress.sh：120 个并发抢同一门课（同一学生限购 1，结果应只有 1 次成功）
TOKEN=<登录拿到的token>
curl -s -X POST http://localhost:8080/api/seckill/init/1001 \
  -H "Authorization: Bearer $TOKEN" > /dev/null
for i in $(seq 1 120); do
  curl -s -X POST http://localhost:8080/api/seckill/1001 \
    -H "Authorization: Bearer $TOKEN" &
done
wait
echo "---- 剩余名额（期望 99） ----"
curl -s http://localhost:8080/api/seckill/stock/1001 -H "Authorization: Bearer $TOKEN"
echo
echo "---- 选课单条数（期望 1，用 mysql 客户端查） ----"
echo "SELECT COUNT(*) FROM enroll_order WHERE order_type=1;"
```

预期：120 并发 → 1 成功 + 119 失败（"限购"/"抢光"），剩余名额 100→99，
`enroll_order` 只有 1 条秒杀单——**无超卖、无重复抢课**。

## 简历核心亮点（5 条）

1. **Nacos 注册中心 + 配置中心**：服务自动注册发现；`mall-common.yaml` 共享配置 +
   `@RefreshScope` 实现改配置不重启（`GET /api/user/config-demo` 可验证）
2. **Sentinel 限流熔断**：抢课接口 QPS 限流 + `blockHandler` 快速失败；
   扣名额接口限流保护 DB；Feign `fallback` 实现名额服务故障时熔断降级
3. **Seata AT 分布式事务**：普通选课 `@GlobalTransactional` 横跨 enroll/seat 两服务，
   配套 `/create-no-seata` 对比接口，直观展示"有事务 vs 脏数据"
4. **Redis + Lua 秒杀防超卖**：Lua 脚本原子扣减（单线程防竞态）+ SETNX 每人限抢 1，
   失败补偿回滚；课程列表 Cache-Aside 缓存，并发读不压垮 MySQL
5. **Gateway 统一鉴权**：`GlobalFilter` 集中验签 Token（白名单放行登录），
   下游经 `X-User-Id` 透传，非法请求在入口 401 拦截

更多面试深挖 Q&A 与现场演示话术见 [docs/INTERVIEW.md](docs/INTERVIEW.md)，
Nacos 配置中心的手动建配置步骤见 [docs/nacos-config.md](docs/nacos-config.md)。

## 目录结构

```
micro-mall/
├── backend/                 # Java 后端（多模块 Maven）
│   ├── common/              # Result/异常/JwtUtil/Lua脚本/Feign配置/跨服务DTO
│   ├── gateway/             # :8080 网关 + 全局鉴权过滤器
│   ├── user-service/        # :8081 登录/个人信息
│   ├── course-service/      # :8082 课程+秒杀抢课
│   ├── enroll-service/      # :8083 选课单（Seata 发起方）
│   ├── seat-service/        # :8084 名额扣减（Seata 分支方）
│   ├── seata/conf/          # seata-server file 模式配置
│   └── Dockerfile           # 统一镜像构建（build-arg MODULE）
├── frontend/                # Vue3 + Vite + Element Plus
│   └── src/views/           # Home（抢课）/ Enroll（确认选课）/ MyCourses（课表）/ Login
├── sql/
│   ├── init.sql             # 建表 + 测试数据（含 100 名额秒杀课程）
│   └── undo_log.sql         # Seata AT 回滚表
├── docs/
│   ├── INTERVIEW.md         # 简历描述 + 8 道深挖 Q&A + 演示话术
│   └── nacos-config.md      # 配置中心手动建配置步骤
├── docker-compose.yml       # 10 容器一键编排
└── README.md
```
