-- =============================================================
-- 校园社团综合管理系统 数据库建表脚本 (MySQL 8)
-- 依据《需求分析与详细设计说明书》2.3 节
-- 执行方式: mysql -uroot -p < schema.sql
-- =============================================================
DROP DATABASE IF EXISTS club_db;
CREATE DATABASE club_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE club_db;

-- -------------------------------------------------------------
-- 1. 用户表 t_user
-- -------------------------------------------------------------
CREATE TABLE t_user (
  user_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  student_no     VARCHAR(12)  NOT NULL COMMENT '学号(登录名)',
  real_name      VARCHAR(20)  NOT NULL COMMENT '姓名',
  password_hash  VARCHAR(100) NOT NULL COMMENT '密码哈希(BCrypt)',
  mobile         CHAR(11)     NOT NULL COMMENT '手机号',
  role_code      VARCHAR(16)  NOT NULL COMMENT 'STUDENT/LEADER/UNION_ADMIN/SYS_ADMIN',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1正常 2锁定',
  created_at     DATETIME     NOT NULL,
  last_login_at  DATETIME     NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_mobile (mobile)
) ENGINE=InnoDB COMMENT='用户登录与档案';

-- -------------------------------------------------------------
-- 2. 社团表 t_club
-- -------------------------------------------------------------
CREATE TABLE t_club (
  club_id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '社团编号',
  leader_id        BIGINT       NOT NULL COMMENT '负责人(FK t_user)',
  club_name        VARCHAR(40)  NOT NULL COMMENT '社团名称(全校唯一)',
  category         VARCHAR(20)  NOT NULL COMMENT '学术/文艺/体育等',
  intro            VARCHAR(500) NULL COMMENT '简介',
  charter          TEXT         NULL COMMENT '章程',
  advisor          VARCHAR(20)  NULL COMMENT '指导老师',
  status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已成立 2已注销',
  recruit_deadline DATETIME     NULL COMMENT '纳新截止,空=长期纳新',
  created_at       DATETIME     NOT NULL,
  PRIMARY KEY (club_id),
  UNIQUE KEY uk_club_name (club_name),
  KEY ix_leader (leader_id),
  CONSTRAINT fk_club_leader FOREIGN KEY (leader_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB COMMENT='社团主体与审核状态';

-- -------------------------------------------------------------
-- 3. 成员资格表 t_membership
-- -------------------------------------------------------------
CREATE TABLE t_membership (
  membership_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录号',
  user_id       BIGINT       NOT NULL COMMENT '学生',
  club_id       BIGINT       NOT NULL COMMENT '社团',
  member_role   VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT 'LEADER/ADMIN/MEMBER',
  apply_reason  VARCHAR(200) NULL COMMENT '申请理由',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1正式成员 2已拒绝 3已退出',
  joined_at     DATETIME     NULL COMMENT '审批通过时写入',
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (membership_id),
  UNIQUE KEY uk_user_club (user_id, club_id),
  KEY ix_club (club_id),
  CONSTRAINT fk_ms_user FOREIGN KEY (user_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_ms_club FOREIGN KEY (club_id) REFERENCES t_club (club_id)
) ENGINE=InnoDB COMMENT='成员资格与入社申请';

-- -------------------------------------------------------------
-- 4. 活动表 t_activity
-- -------------------------------------------------------------
CREATE TABLE t_activity (
  activity_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动编号',
  club_id        BIGINT       NOT NULL COMMENT '所属社团',
  venue_app_id   BIGINT       NULL COMMENT '场地申请单(0..1)',
  title          VARCHAR(40)  NOT NULL COMMENT '活动名称',
  act_type       TINYINT      NOT NULL DEFAULT 0 COMMENT '0内部 1公开',
  start_time     DATETIME     NOT NULL COMMENT '开始时间',
  end_time       DATETIME     NOT NULL COMMENT '结束时间',
  location       VARCHAR(80)  NOT NULL COMMENT '地点',
  capacity       INT          NOT NULL COMMENT '名额(1-500)',
  remain         INT          NOT NULL COMMENT '剩余名额(条件更新防超卖)',
  signup_deadline DATETIME    NOT NULL COMMENT '报名截止(早于开始时间)',
  intro          VARCHAR(500) NULL COMMENT '简介',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已发布 2进行中 3已结束 4已取消',
  created_at     DATETIME     NOT NULL,
  PRIMARY KEY (activity_id),
  KEY ix_club_status_time (club_id, status, start_time),
  KEY ix_status_time (status, start_time),
  CONSTRAINT fk_act_club FOREIGN KEY (club_id) REFERENCES t_club (club_id),
  CONSTRAINT fk_act_venue_app FOREIGN KEY (venue_app_id) REFERENCES t_venue_application (app_id),
  CONSTRAINT chk_capacity CHECK (capacity BETWEEN 1 AND 500)
) ENGINE=InnoDB COMMENT='活动主体与状态';

-- -------------------------------------------------------------
-- 5. 报名记录表 t_registration
-- -------------------------------------------------------------
CREATE TABLE t_registration (
  reg_id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '报名编号',
  activity_id     BIGINT      NOT NULL COMMENT '活动',
  user_id         BIGINT      NOT NULL COMMENT '学生',
  activity_title  VARCHAR(40) NOT NULL COMMENT '活动名称快照',
  start_time      DATETIME    NOT NULL COMMENT '活动开始时间快照',
  voucher_code    VARCHAR(16) NOT NULL COMMENT '凭证码(随机唯一)',
  status          TINYINT     NOT NULL DEFAULT 0 COMMENT '0已报名 1已签到 2已取消 3候补',
  checkin_time    DATETIME    NULL COMMENT '签到时间',
  created_at      DATETIME    NOT NULL,
  PRIMARY KEY (reg_id),
  UNIQUE KEY uk_act_user (activity_id, user_id),
  UNIQUE KEY uk_voucher (voucher_code),
  KEY ix_user_status (user_id, status),
  CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES t_activity (activity_id),
  CONSTRAINT fk_reg_user FOREIGN KEY (user_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB COMMENT='活动报名与签到记录';

-- -------------------------------------------------------------
-- 6. 场地表 t_venue
-- -------------------------------------------------------------
CREATE TABLE t_venue (
  venue_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '场地编号',
  venue_name  VARCHAR(40) NOT NULL COMMENT '场地名称',
  capacity    INT         NOT NULL COMMENT '可容纳人数',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0停用 1可用',
  created_at  DATETIME    NOT NULL,
  PRIMARY KEY (venue_id),
  UNIQUE KEY uk_venue_name (venue_name)
) ENGINE=InnoDB COMMENT='场地';

-- -------------------------------------------------------------
-- 7. 场地申请表 t_venue_application
--    时段冲突兜底: 场地+日期+时段+状态 唯一
-- -------------------------------------------------------------
CREATE TABLE t_venue_application (
  app_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请编号',
  venue_id     BIGINT       NOT NULL COMMENT '场地',
  activity_id  BIGINT       NULL COMMENT '关联活动(可先占场地再建活动)',
  applicant_id BIGINT       NOT NULL COMMENT '申请人',
  use_date     DATE         NOT NULL COMMENT '使用日期',
  time_slot    VARCHAR(20)  NOT NULL COMMENT '时段,如 14:00-16:00',
  purpose      VARCHAR(200) NOT NULL COMMENT '用途',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回 3已释放',
  audit_remark VARCHAR(255) NULL COMMENT '审批意见',
  created_at   DATETIME     NOT NULL,
  PRIMARY KEY (app_id),
  UNIQUE KEY uk_venue_slot_status (venue_id, use_date, time_slot, status),
  KEY ix_venue_date (venue_id, use_date),
  CONSTRAINT fk_va_venue FOREIGN KEY (venue_id) REFERENCES t_venue (venue_id),
  CONSTRAINT fk_va_activity FOREIGN KEY (activity_id) REFERENCES t_activity (activity_id),
  CONSTRAINT fk_va_applicant FOREIGN KEY (applicant_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB COMMENT='场地申请单与时段占用';

-- -------------------------------------------------------------
-- 8. 公告表 t_notice
-- -------------------------------------------------------------
CREATE TABLE t_notice (
  notice_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告编号',
  club_id      BIGINT       NULL COMMENT '所属社团,空=校级公告',
  publisher_id BIGINT       NOT NULL COMMENT '发布人',
  title        VARCHAR(40)  NOT NULL COMMENT '标题',
  content      TEXT         NOT NULL COMMENT '正文',
  scope        TINYINT      NOT NULL DEFAULT 1 COMMENT '0本社团 1全校',
  is_pinned    TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶,校级最多3条',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0已撤回 1已发布',
  created_at   DATETIME     NOT NULL,
  PRIMARY KEY (notice_id),
  KEY ix_scope_pin_time (scope, is_pinned, created_at),
  CONSTRAINT fk_notice_club FOREIGN KEY (club_id) REFERENCES t_club (club_id),
  CONSTRAINT fk_notice_publisher FOREIGN KEY (publisher_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB COMMENT='公告';

-- -------------------------------------------------------------
-- 9. 审批记录表 t_audit_log
-- -------------------------------------------------------------
CREATE TABLE t_audit_log (
  log_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  biz_type   VARCHAR(16)  NOT NULL COMMENT 'CLUB/ACTIVITY/VENUE_APP',
  biz_id     BIGINT       NOT NULL COMMENT '多态关联业务编号',
  auditor_id BIGINT       NOT NULL COMMENT '审核人',
  result     TINYINT      NOT NULL COMMENT '0驳回 1通过',
  remark     VARCHAR(255) NULL COMMENT '意见',
  created_at DATETIME     NOT NULL,
  PRIMARY KEY (log_id),
  KEY ix_biz (biz_type, biz_id),
  CONSTRAINT fk_audit_auditor FOREIGN KEY (auditor_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB COMMENT='审批与关键操作日志';
