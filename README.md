# 校园社团综合管理系统

软件课程设计项目。前后端分离的校园社团管理平台，覆盖社团成立审批、入社申请审批、活动发布与报名签到、场地申请与时段冲突控制、公告发布、数据统计等完整业务闭环。

## 团队分工

| 成员 | 职责 |
|---|---|
| 张斯元（组长） | 前端全部页面、数据库环境与 SQL 维护、文档共同维护 |
| 王蓉 | 后端：用户认证、社团、成员资格模块 |
| 赵梦涵 | 后端：活动、场地、统计、公告模块 |

## 技术栈

- **后端**：Spring Boot 2.7.18 + MyBatis（注解 SQL）+ MySQL 8.0 + JWT 认证（Java 8+ 均可运行）
- **前端**：Vue 3（Composition API）+ Vite + Element Plus + axios + vue-router + pinia
- **统一响应体**：`{code, message, data}`，成功 `code = 0`

## 目录结构

```
├── schema.sql              # 建库建表脚本（权威版本，含 ALTER TABLE 解循环外键）
├── sql/demo-data.sql       # 演示数据（已内置 SET NAMES utf8mb4）
├── backend/                # Spring Boot 后端（src/main/java/com/club）
│   ├── controller/         # 接口层（Auth/Club/Membership/Activity/Venue/Notice/Stats/Admin*）
│   ├── service/            # 业务逻辑（含单测覆盖的核心规则）
│   ├── mapper/             # MyBatis 数据访问
│   ├── security/           # JWT 解析 + 拦截器 + 角色白名单
│   └── resources/application.yml  # 端口/数据库/JWT 配置
└── frontend/               # Vue3 前端
    └── src/
        ├── api/            # 按模块拆分的接口封装（axios 拦截器统一解包/带 token）
        ├── views/          # 页面（对应详细设计说明书表 2-9）
        ├── router/         # 路由表 + 登录守卫
        ├── stores/         # pinia 用户会话
        └── utils/mask.js   # 学号/手机号脱敏（安全需求 S-04）
```

## 环境要求

- JDK 8 或以上、Maven 3.6+、MySQL 8.0、Node.js 16+

## 快速启动

### 1. 初始化数据库

```sql
CREATE DATABASE club_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE club_db;
SOURCE /path/to/schema.sql;        -- 建表
SOURCE /path/to/sql/demo-data.sql; -- 演示数据
```

Windows 下如遇中文乱码，先执行 `SET NAMES utf8mb4;` 再 SOURCE。

### 2. 启动后端（端口 8080）

修改 `backend/src/main/resources/application.yml` 中的数据库密码后：

```bash
cd backend
mvn spring-boot:run
```

### 3. 启动前端（端口 5173，已配置 /api 代理到 8080）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173

## 演示账号（密码统一 `123456`）

| 学号 | 姓名 | 角色 | 用途 |
|---|---|---|---|
| 20260001 | 系统管理员 | SYS_ADMIN | 系统级管理 |
| 20260002 | 社联管理员 | UNION_ADMIN | 社团/活动/场地审批、校级公告、统计报表 |
| 20260003 | 张斯元 | LEADER | 计算机协会社长：入社审批、发布活动、申请场地、社团公告 |
| 20260004 | 王蓉 | LEADER | 街舞社社长 |
| 20260005-08 | 学生 | STUDENT | 浏览社团、申请入社、活动报名签到 |

## 核心业务规则（答辩演示点）

1. **场地时段冲突防重**：同一场地同一日期时段已有"已通过"申请时，重复申请被 409 拒绝（唯一键兜底防并发）
2. **活动满额不超卖**：报名走"条件更新 `remain > 0`"，满额后自动转候补
3. **取消释放名额**：取消报名后名额即时回补，候补递补
4. **校级置顶公告上限**：同时最多 3 条，超出 409（文档表 2-18）
5. **入社审批闭环**：学生申请 → 社长审批（拒绝必填原因）→ 正式成员名单实时更新，全程落审计表
6. **隐私脱敏（S-04）**：学号 `2026**06`、手机号 `138****0005`，展示他人信息的页面一律脱敏

## 页面清单（对应文档表 2-9）

| 文档页面 | 实现路由 | 文档页面 | 实现路由 |
|---|---|---|---|
| login / register | `/login` `/register` | members | `/members` |
| clubList / clubDetail | `/clubList` `/clubDetail` | applyList（入社审批） | `/memberAudit` |
| clubCreate | `/clubCreate` | myClubs | `/myClubs` |
| activityList / Detail / Create | `/activityList` `/activityDetail` `/activityCreate` | notice / noticeEdit | `/notice`（发布并入弹窗） |
| checkIn / myRegistrations | `/checkIn` `/myRegistrations` | venueApply | `/venueApply` |
| clubAudit / activityAudit / venueAudit | 合并为 `/applyList` 三 tab | stats | `/stats` |

> 注：文档中的 applyList.html 指"入社审批"，实现中 `/applyList` 为社联三合一审批工作台，入社审批为 `/memberAudit`，以本表为准。
