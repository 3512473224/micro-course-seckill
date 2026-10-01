-- ============================================================
-- Seata AT 模式回滚表：每个参与分布式事务的库都要建一份。
-- 演示环境一库多表，建一次即可；生产 enroll 库、seat 库各建一份。
--
-- 原理回顾：AT 一阶段执行业务 SQL 前后，Seata 代理会记录"前镜像/后镜像"
-- 到这张表；二阶段回滚时用前镜像生成反向 SQL 做补偿。
-- ============================================================
USE mall;

CREATE TABLE IF NOT EXISTS undo_log (
  branch_id    BIGINT       NOT NULL COMMENT '分支事务 id',
  xid          VARCHAR(128) NOT NULL COMMENT '全局事务 id',
  context      VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB    NOT NULL COMMENT '前后镜像，回滚依据',
  log_status   INT          NOT NULL COMMENT '0=正常 1=已回滚',
  log_created  DATETIME(6)  NOT NULL,
  log_modified DATETIME(6)  NOT NULL,
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT 回滚日志表';
