-- ============================================================
-- 校园抢课系统初始化脚本（MySQL 8）
-- 说明：演示环境"一库多表"；生产按服务垂直分库：
--   user                                    -> user-service（本演示为内存 mock，建表仅作对照）
--   course / seckill_course / course_phase /
--   course_review                           -> course-service
--   enroll_order / enroll_wish / waitlist    -> enroll-service
--   seat                                    -> seat-service
--   undo_log                                -> 每个库一份（Seata AT 回滚用，见 undo_log.sql）
-- 可重复执行：先 DROP 全量表再重建。
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE mall;

DROP TABLE IF EXISTS course_review;
DROP TABLE IF EXISTS waitlist;
DROP TABLE IF EXISTS enroll_wish;
DROP TABLE IF EXISTS course_phase;
DROP TABLE IF EXISTS enroll_order;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS seckill_course;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS `user`;

-- ---------------- 课程表 ----------------
CREATE TABLE course (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程 id',
  name          VARCHAR(128) NOT NULL COMMENT '课程名',
  teacher       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '授课教师姓名（快照）',
  credit        DECIMAL(4,1) NOT NULL DEFAULT 2.0 COMMENT '学分',
  description   VARCHAR(512) NOT NULL DEFAULT '' COMMENT '课程简介',
  image         VARCHAR(256) NOT NULL DEFAULT '' COMMENT '封面图（演示用 emoji/占位）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=上架可选 0=下架/草稿',
  weekday       TINYINT      NULL COMMENT '上课星期 1-7',
  start_section TINYINT      NULL COMMENT '起始节次',
  end_section   TINYINT      NULL COMMENT '结束节次',
  weeks         VARCHAR(32)  NOT NULL DEFAULT '1-16' COMMENT '教学周，如 1-16、1-8,10-16',
  classroom     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '上课教室，如 教3-201',
  teacher_id    BIGINT       NULL COMMENT '授课教师的用户 id（教师开课时写入）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- ---------------- 秒杀课程表 ----------------
CREATE TABLE seckill_course (
  course_id   BIGINT   NOT NULL COMMENT '课程 id',
  stock_count INT      NOT NULL COMMENT '本次抢课放出的名额数',
  start_time  DATETIME NOT NULL COMMENT '开抢时间',
  end_time    DATETIME NOT NULL COMMENT '结束时间',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1=有效 0=失效',
  PRIMARY KEY (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀课程表';

-- ---------------- 名额表 ----------------
CREATE TABLE seat (
  course_id BIGINT NOT NULL COMMENT '课程 id',
  total     INT    NOT NULL COMMENT '总名额',
  available INT    NOT NULL COMMENT '剩余可抢名额',
  frozen    INT    NOT NULL DEFAULT 0 COMMENT '已占名额（演示字段）',
  version   INT    NOT NULL DEFAULT 0 COMMENT '乐观锁预留',
  PRIMARY KEY (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程名额表';

-- ---------------- 选课单表 ----------------
CREATE TABLE enroll_order (
  id          BIGINT         NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(32)    NOT NULL COMMENT '业务单号',
  user_id     BIGINT         NOT NULL COMMENT '学生 id',
  course_id   BIGINT         NOT NULL COMMENT '课程 id',
  course_name VARCHAR(128)   NOT NULL DEFAULT '',
  credit      DECIMAL(4,1)   NOT NULL DEFAULT 2.0,
  order_type  TINYINT        NOT NULL DEFAULT 0 COMMENT '0=普通选课 1=秒杀抢课 2=志愿录取 3=候补转正',
  status      TINYINT        NOT NULL DEFAULT 1 COMMENT '0=待确认 1=已选上 2=已取消',
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课单表';

-- ---------------- 用户表（对照用，user-service 实际走内存 mock） ----------------
CREATE TABLE `user` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(64)  NOT NULL,
  password   VARCHAR(128) NOT NULL COMMENT '演示明文，生产用 BCrypt',
  nickname   VARCHAR(64)  NOT NULL DEFAULT '',
  role       VARCHAR(16)  NOT NULL DEFAULT 'student' COMMENT 'student=学生 teacher=教师 admin=管理员',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------- 选课阶段表 ----------------
CREATE TABLE course_phase (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64)  NOT NULL COMMENT '阶段名称',
  type       VARCHAR(16)  NOT NULL COMMENT 'WISH=志愿填报 MAIN=正选 ADD=补选',
  start_time DATETIME     NOT NULL,
  end_time   DATETIME     NOT NULL,
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=关闭 1=进行中 2=已结束',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课阶段表';

-- ---------------- 志愿填报表 ----------------
CREATE TABLE enroll_wish (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  student_id BIGINT   NOT NULL COMMENT '学生 id',
  course_id  BIGINT   NOT NULL COMMENT '课程 id',
  priority   TINYINT  NOT NULL DEFAULT 1 COMMENT '1=第一志愿 2=第二志愿',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course (student_id, course_id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿填报表';

-- ---------------- 候补队列表 ----------------
CREATE TABLE waitlist (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  student_id BIGINT   NOT NULL COMMENT '学生 id',
  course_id  BIGINT   NOT NULL COMMENT '课程 id',
  position   INT      NOT NULL COMMENT '队列位置：课程内递增，越小越先补位',
  status     TINYINT  NOT NULL DEFAULT 0 COMMENT '0=排队中 1=已补位 2=已取消',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_course_status (course_id, status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补队列表';

-- ---------------- 课程评价表 ----------------
CREATE TABLE course_review (
  id         BIGINT         NOT NULL AUTO_INCREMENT,
  student_id BIGINT         NOT NULL COMMENT '评价学生 id',
  course_id  BIGINT         NOT NULL COMMENT '课程 id',
  score      TINYINT        NOT NULL COMMENT '1-5 分',
  comment    VARCHAR(512)   NOT NULL DEFAULT '',
  created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course (student_id, course_id),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程评价表';

-- ================= 测试数据 =================
INSERT INTO `user` (id, username, password, nickname, role) VALUES
  (1, 'student01', '123456', '张同学', 'student'),
  (2, 'student02', '123456', '李同学', 'student'),
  (4, 'teacher01', '123456', '王教授', 'teacher'),
  (3, 'admin', '123456', '教务管理员', 'admin');

-- 选课阶段：志愿填报已结束，正选进行中（2026-09-08~2026-10-15），补选未开始
INSERT INTO course_phase (id, name, type, start_time, end_time, status) VALUES
  (1, '2026年秋季学期志愿填报', 'WISH', '2026-09-01 00:00:00', '2026-09-07 23:59:59', 2),
  (2, '2026年秋季学期正选', 'MAIN', '2026-09-08 00:00:00', '2026-10-15 23:59:59', 1),
  (3, '2026年秋季学期补选', 'ADD', '2026-10-16 00:00:00', '2026-10-20 23:59:59', 0);

-- 22 门课程：真实课程名、教师名、上课时间（星期/节次/教学周）、教室、学分
-- teacher_id=4 的两门是 teacher01（王教授）所授，用于演示教师端"我的课程/花名册"
INSERT INTO course
  (id, name, teacher, teacher_id, credit, description, status,
   weekday, start_section, end_section, weeks, classroom) VALUES
  (1, '数据结构', '王教授', 4, 3.0, '线性表、树、图、排序与查找，计科核心基础课', 1, 1, 1, 2, '1-16', '教3-201'),
  (2, '操作系统', '王教授', 4, 3.0, '进程管理、内存管理、文件系统，考研重点', 1, 2, 3, 4, '1-16', '教3-202'),
  (3, '高等数学', '陈教授', NULL, 4.0, '函数、极限、微积分，理工科必修', 1, 1, 3, 4, '1-16', '教1-101'),
  (4, '大学物理', '刘教授', NULL, 3.0, '力学、电磁学、光学，含实验课', 1, 3, 1, 2, '1-16', '教1-102'),
  (5, '线性代数', '赵教授', NULL, 2.5, '行列式、矩阵、向量空间', 1, 4, 1, 2, '1-16', '教2-301'),
  (6, '概率论与数理统计', '钱教授', NULL, 2.5, '随机变量、分布、参数估计与假设检验', 1, 5, 3, 4, '1-16', '教2-302'),
  (7, '计算机网络', '孙教授', NULL, 2.5, 'TCP/IP、HTTP、网络编程，附带实验', 1, 2, 5, 6, '1-16', '教3-203'),
  (8, '数据库系统', '周教授', NULL, 3.0, '关系模型、SQL、事务与索引，含课程设计', 1, 4, 3, 4, '1-16', '机房A-401'),
  (9, '软件工程', '吴教授', NULL, 2.5, '需求分析、面向对象设计、敏捷开发', 1, 3, 5, 6, '1-16', '教3-204'),
  (10, '人工智能导论', '郑教授', NULL, 2.0, '回归、分类、聚类与神经网络入门', 1, 5, 1, 2, '1-16', '教4-101'),
  (11, 'Python程序设计', '褚老师', NULL, 2.0, '语法基础、数据分析入门，上机为主', 1, 1, 5, 6, '1-12', '机房B-402'),
  (12, '大学英语', '蒋老师', NULL, 2.0, '听说读写综合训练，分级教学', 1, 2, 1, 2, '1-16', '教2-101'),
  (13, '体育-羽毛球', '沈教练', NULL, 1.0, '体育选修，零基础可报，自备球拍', 1, 4, 7, 8, '1-16', '体育馆'),
  (14, '音乐鉴赏', '韩老师', NULL, 1.5, '中西方经典音乐作品赏析', 1, 3, 7, 8, '1-16', '艺术楼-201'),
  (15, '心理学导论', '杨教授', NULL, 2.0, '认知、情绪、人格与社会心理学入门', 1, 5, 5, 6, '1-16', '教4-102'),
  (16, '经济学原理', '朱教授', NULL, 2.0, '微观与宏观经济学基础', 1, 1, 7, 8, '1-16', '教5-201'),
  (17, '书法鉴赏', '秦老师', NULL, 1.0, '篆隶楷行草五体赏析与临摹', 1, 2, 7, 8, '1-16', '艺术楼-301'),
  (18, '摄影基础', '尤老师', NULL, 1.5, '构图、用光与后期，自备相机优先', 1, 4, 5, 6, '1-12', '艺术楼-202'),
  (19, '志愿服务实践', '许老师', NULL, 1.0, '社区服务一线实践，按服务时长计分', 1, 6, 1, 4, '1-8', '校外实践基地'),
  (20, '创新创业基础', '何教授', NULL, 2.0, '商业模式、路演与创业计划书', 1, 3, 3, 4, '1-16', '教5-202'),
  (21, '离散数学', '施教授', NULL, 3.0, '集合论、图论、数理逻辑，计科数学基础', 1, 2, 3, 4, '1-16', '教3-205'),
  (22, '毛泽东思想和中国特色社会主义理论体系概论', '张教授', NULL, 3.0, '思政必修课', 1, 4, 3, 4, '1-16', '教5-203');

-- 秒杀课程：id 1001《Python 数据分析实战》，放出 100 个名额
INSERT INTO course
  (id, name, teacher, teacher_id, credit, description, status,
   weekday, start_section, end_section, weeks, classroom) VALUES
  (1001, 'Python 数据分析实战（秒杀）', '陈教授', NULL, 2.0,
   'Pandas/Matplotlib 实战，限 100 个名额，先到先得', 1, 6, 1, 2, '1-16', '机房B-402');

INSERT INTO seckill_course (course_id, stock_count, start_time, end_time, status) VALUES
  (1001, 100, DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 10 HOUR,
              DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 12 HOUR, 1);

-- 名额：与课程容量对应
INSERT INTO seat (course_id, total, available) VALUES
  (1, 120, 120), (2, 120, 120), (3, 150, 150), (4, 150, 150),
  (5, 120, 120), (6, 120, 120), (7, 100, 100), (8, 90, 90),
  (9, 100, 100), (10, 80, 80), (11, 90, 90), (12, 60, 60),
  (13, 40, 40), (14, 80, 80), (15, 100, 100), (16, 100, 100),
  (17, 50, 50), (18, 50, 50), (19, 60, 60), (20, 120, 120),
  (21, 110, 110), (22, 150, 150),
  (1001, 100, 100);
