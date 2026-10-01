# Nacos 配置中心：需要手动创建的配置

> 服务发现（注册中心）是零配置的：服务启动自动注册到 `nacos:8848`。
> 下面这个是**配置中心**的演示配置，不建也不影响启动；建了之后可以演示"动态刷新"。

## 配置 1：mall-common.yaml（所有服务共享）

- **dataId**：`mall-common.yaml`
- **group**：`DEFAULT_GROUP`
- **配置格式**：YAML

内容示例：

```yaml
mall:
  demo:
    # 改这里，然后调 GET /api/user/config-demo，会发现文案变了——服务没重启！
    # 原理：bootstrap.yml 里 shared-configs 配了 refresh=true + Controller 上 @RefreshScope
    banner: "🔥 抢课专场进行中（这段文案来自 Nacos 配置中心，可动态刷新）"
```

## 创建方式（二选一）

### 方式 A：控制台页面（推荐演示用）

1. 打开 http://localhost:8848/nacos（本项目 compose 已关闭鉴权，直接进）
2. 左侧菜单 → **配置管理 → 配置列表** → 右上角 **+** 创建配置
3. dataId 填 `mall-common.yaml`，group 选 `DEFAULT_GROUP`，格式选 YAML，粘贴上面的内容，发布

### 方式 B：curl 命令

```bash
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=mall-common.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=mall:
  demo:
    banner: 🔥 抢课专场进行中（这段文案来自 Nacos 配置中心，可动态刷新）"
```

## 验证动态刷新

```bash
# 1. 先看当前值
curl -s http://localhost:8080/api/user/config-demo \
  -H "Authorization: Bearer <登录拿到的token>"

# 2. 去 Nacos 控制台改 banner 内容并发布

# 3. 再调一次接口，文案已变，全程无重启
curl -s http://localhost:8080/api/user/config-demo \
  -H "Authorization: Bearer <登录拿到的token>"
```

> 扩展（面试可聊）：生产环境 Nacos 配置推荐按 `服务名.yaml` + `共享.yaml` 分层；
> 敏感配置（DB 密码）用 Nacos 的加密插件或 Vault；多环境用 namespace 隔离（dev/test/prod）。
