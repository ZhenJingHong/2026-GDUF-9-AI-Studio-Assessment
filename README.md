# 个人记账与消费分析 AI 助手

> 2026 GDUF 人工智能工作室二轮考核 · 方向一（AI 应用）· 选题 A

## 项目简介

传统记账要逐笔选分类、填金额，一笔消费五六次点击，坚持不了几周。本项目让用户用一句话（「昨天打车 32 块」）或一张小票照片完成记账，系统自动解析、分类、入库；查账时直接问「上个月吃饭花了多少」，不用在筛选器里点半天。

**目标**：把记账的输入成本降到一句话或一张图，并让统计、预算这些信息真正建立在入库数据之上。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 25、Spring Boot 4.1.2、Spring Data JPA、Maven Wrapper |
| 数据库 | H2（开发期，内存模式） |
| 前端 | Vue 3 + Vite + Element Plus + ECharts |
| AI | OpenAI 兼容格式的大模型 API（默认 DeepSeek），小票识别用多模态模型 |
| 部署 | Docker / docker-compose |

## 目录结构

```text
.
├── src/main/java/com/example/backend/
│   ├── BackendApplication.java
│   ├── ai/            # AI 调用与解析
│   ├── bill/          # 账单实体与 CRUD
│   └── stats/         # 统计接口
├── src/main/resources/
│   └── application.properties
├── frontend/          # Vue3 前端（规划中）
├── docs/
│   ├── 接口文档.md
│   └── 设计说明.md
├── Dockerfile
└── README.md
```

## 快速开始

### 环境要求

- JDK 25
- 无需单独安装 Maven，仓库自带 `mvnw`

### 启动后端

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux 用 `./mvnw spring-boot:run`。

启动后访问 <http://localhost:8080>，看到 Whitelabel Error Page 说明服务已起（目前还没有页面路由）。

### 配置 AI API Key

```powershell
$env:AI_API_KEY="sk-你的key"
```

`application.properties` 中读取：

```properties
ai.base-url=https://api.deepseek.com/v1
ai.api-key=${AI_API_KEY}
ai.model=deepseek-chat
```

> ⚠️ 真实 Key 不得提交到仓库，本地配置放 `application-local.properties`（已加入 `.gitignore`）。

## 功能进度

| 功能 | 状态 | 负责人 |
| --- | --- | --- |
| Spring Boot 骨架 | ✅ 已完成 | — |
| 账单实体与 CRUD | 🚧 开发中 | 邱卓成 |
| AI 自然语言记账 | 🚧 开发中 | <待填> |
| 小票 OCR 识别 | ⬜ 未开始 | <待填> |
| 自动分类与统计 | 🚧 开发中 | 陈宇亮 |
| 自然语言查账 | ⬜ 未开始 | <待填> |
| 预算与超支提醒 | ⬜ 未开始 | 待认领 |
| 前端页面 | 🚧 开发中 | 麦苗 |
| Docker 部署 | ⬜ 未开始 | 待认领 |
| 答辩 PPT 与录屏 | ⬜ 未开始 | IdyllSurge |

## 接口一览

当前仅骨架，接口随开发进度更新，字段定义见 [`docs/接口文档.md`](docs/接口文档.md)。

| 方法 | 路径 | 说明 | 状态 |
| --- | --- | --- | --- |
| GET | `/api/ai/ping` | 健康检查 | 🚧 |
| POST | `/api/ai/parse-bill` | 一句话 → 账单 JSON | 🚧 |
| POST | `/api/bills` | 新增账单 | 🚧 |
| GET | `/api/bills` | 查询账单 | 🚧 |
| GET | `/api/stats/category` | 按类别统计 | 🚧 |

## 团队分工

| 学号 | 姓名 | 负责模块 |
| --- | --- | --- |
| 251731429 | 邱卓成 | 账单基础模块（实体、数据库、CRUD 接口） |
| 251735112 | 麦苗 | 前端页面、接口文档 |
| 251735331 | 陈宇亮 | 统计与图表数据接口 |
| 251735254 | 陈跃龙 | README、项目文档、答辩 PPT、录屏 |
| 261731434 | 钟嘉和 | AI/API 调用（自然语言解析、OCR、查账意图） |

## 已知问题与未完成部分

- 前端尚未接入，目前只有后端接口
- OCR 识别精度依赖模型，模糊小票可能失败，需要用户手动修正
- 预算提醒尚未实现
- 数据库暂用 H2 内存模式，重启数据丢失，后续切换到文件模式或 MySQL
- Docker 镜像尚未提供

## 提交规范

- 每完成一个可运行的小功能提交一次，不要最后一次性上传
- commit message 用 `type: 说明`，例如 `feat: 新增账单新增接口`、`docs: 补充接口文档`
- 不提交 `target/`、`.idea/`、`node_modules/`、API Key