-- ============================================================
-- 校园抢课系统初始化脚本（MySQL 8）
-- 说明：演示环境"一库多表"；生产按服务垂直分库：
--   user   -> user-service（本演示为内存 mock，建表仅作对照）
--   course / seckill_course -> course-service
--   enroll_order            -> enroll-service
--   seat                    -> seat-service
--   undo_log                -> 每个库一份（Seata AT 回滚用，见 undo_log.sql）
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE mall;

-- ---------------- 课程表 ----------------
CREATE TABLE IF NOT EXISTS course (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程 id',
  name        VARCHAR(128) NOT NULL COMMENT '课程名',
  teacher     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '授课教师',
  credit      DECIMAL(4,1) NOT NULL DEFAULT 2.0 COMMENT '学分',
  description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '课程简介',
  image       VARCHAR(256) NOT NULL DEFAULT '' COMMENT '封面图（演示用 emoji/占位）',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=上架可选 0=下架',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- ---------------- 秒杀课程表 ----------------
CREATE TABLE IF NOT EXISTS seckill_course (
  course_id   BIGINT   NOT NULL COMMENT '课程 id',
  stock_count INT      NOT NULL COMMENT '本次抢课放出的名额数',
  start_time  DATETIME NOT NULL COMMENT '开抢时间',
  end_time    DATETIME NOT NULL COMMENT '结束时间',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1=有效 0=失效',
  PRIMARY KEY (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀课程表';

-- ---------------- 名额表 ----------------
CREATE TABLE IF NOT EXISTS seat (
  course_id BIGINT NOT NULL COMMENT '课程 id',
  total     INT    NOT NULL COMMENT '总名额',
  available INT    NOT NULL COMMENT '剩余可抢名额',
  frozen    INT    NOT NULL DEFAULT 0 COMMENT '已占名额（演示字段）',
  version   INT    NOT NULL DEFAULT 0 COMMENT '乐观锁预留',
  PRIMARY KEY (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程名额表';

-- ---------------- 选课单表 ----------------
CREATE TABLE IF NOT EXISTS enroll_order (
  id          BIGINT         NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(32)    NOT NULL COMMENT '业务单号',
  user_id     BIGINT         NOT NULL COMMENT '学生 id',
  course_id   BIGINT         NOT NULL COMMENT '课程 id',
  course_name VARCHAR(128)   NOT NULL DEFAULT '',
  credit      DECIMAL(4,1)   NOT NULL DEFAULT 2.0,
  order_type  TINYINT        NOT NULL DEFAULT 0 COMMENT '0=普通选课 1=秒杀抢课',
  status      TINYINT        NOT NULL DEFAULT 1 COMMENT '0=待确认 1=已选上 2=已取消',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课单表';

-- ---------------- 用户表（对照用，user-service 实际走内存 mock） ----------------
CREATE TABLE IF NOT EXISTS user (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(64)  NOT NULL,
  password   VARCHAR(128) NOT NULL COMMENT '演示明文，生产用 BCrypt',
  nickname   VARCHAR(64)  NOT NULL DEFAULT '',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ================= 测试数据 =================
INSERT INTO user (username, password, nickname) VALUES
  ('student01', '123456', '张同学'),
  ('student02', '123456', '李同学'),
  ('admin', 'admin123', '教务管理员')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

-- 普通课程（id 1~6）
INSERT INTO course (id, name, teacher, credit, description, image, status) VALUES
  (1, '数据结构', '王教授', 3.0, '线性表、树、图、排序与查找，计科核心基础课', '📊', 1),
  (2, '操作系统', '李教授', 3.0, '进程管理、内存管理、文件系统，考研重点', '💻', 1),
  (3, '计算机网络', '赵教授', 2.5, 'TCP/IP、HTTP、网络编程，附带实验', '🌐', 1),
  (4, '机器学习导论', '陈教授', 2.0, '回归、分类、聚类与神经网络入门', '🤖', 1),
  (5, '大学英语演讲', '刘老师', 1.5, '英语演讲与辩论，锻炼口语表达', '🎤', 1),
  (6, '羽毛球初级班', '孙教练', 1.0, '体育选修，零基础可报', '🏸', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 秒杀课程：id 1001《Python 数据分析实战》，放出 100 个名额
-- start_time 设为"明天 10:00"，end_time 为"明天 12:00"，方便演示倒计时；
-- 如需立即开抢，把 start_time 改为过去时间即可。
INSERT INTO course (id, name, teacher, credit, description, image, status) VALUES
  (1001, 'Python 数据分析实战（秒杀）', '陈教授', 2.0, 'Pandas/Matplotlib 实战，限 100 个名额，先到先得', '🔥', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO seckill_course (course_id, stock_count, start_time, end_time, status) VALUES
  (1001, 100, DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 10 HOUR,
              DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 12 HOUR, 1)
ON DUPLICATE KEY UPDATE stock_count = VALUES(stock_count);

-- 名额：普通课程各 200，秒杀课程 100（与 seckill_course.stock_count 一致）
INSERT INTO seat (course_id, total, available) VALUES
  (1, 200, 200), (2, 200, 200), (3, 200, 200),
  (4, 200, 200), (5, 200, 200), (6, 200, 200),
  (1001, 100, 100)
ON DUPLICATE KEY UPDATE total = VALUES(total), available = VALUES(available);
