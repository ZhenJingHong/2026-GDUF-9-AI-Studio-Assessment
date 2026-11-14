# 个人记账与消费分析 AI 助手

> 2026 GDUF 人工智能工作室二轮考核 · 方向一（AI 应用）· 选题 A

## 项目简介

传统记账要逐笔选分类、填金额，一笔消费五六次点击，坚持不了几周。本项目让用户用一句话（「昨天打车 32 块」）或一张小票照片完成记账，系统自动解析、分类、入库；查账时直接问「上个月吃饭花了多少」，不用在筛选器里点半天。

**目标**：把记账的输入成本降到一句话或一张图，并让统计、预算这些信息真正建立在入库数据之上。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 25、Spring Boot 4.1.2、Spring Data JPA、Maven Wrapper |
| 数据库 | H2（文件模式，数据持久化到 `./data`） |
| 前端 | Vue 3 + Vite + ECharts + Lucide 图标 + pnpm |
| AI | DeepSeek `deepseek-flash`（支持图片输入，OpenAI 兼容格式） |
| 部署 | Docker 多阶段构建 / docker-compose |

## 目录结构

```text
.
├── src/main/java/com/example/backend/
│   ├── BackendApplication.java
│   ├── ai/                    # AI 调用：一句话解析、小票识别、查账意图
│   ├── bill/                  # 账单实体、Repository、CRUD 接口
│   ├── common/                # 统一响应结构 ApiResponse
│   └── stat/                  # 统计接口（分类 / 趋势 / 日期范围）
├── src/main/resources/
│   └── application.properties
├── src/test/java/com/example/backend/
│   ├── BackendApplicationTests.java
│   └── stat/StatsApiTests.java
├── frontend/                  # Vue3 前端，Vite 代理 /api 到 8080
├── docs/
│   ├── 接口文档.md
│   ├── 设计说明.md
│   ├── sample-bills.csv
│   └── import-sample.ps1
├── Dockerfile                 # 三阶段构建：前端 → 后端 → 运行
├── docker-compose.yaml
├── pom.xml
└── README.md
```

## 环境要求

- JDK 25
- Node.js 20+ 与 pnpm（仅前端开发需要）
- Docker（可选，用于容器化部署）
- 无需单独安装 Maven，仓库自带 `mvnw`

## 快速开始

### 1. 启动后端

```powershell
$env:AI_API_KEY="sk-你的key"
.\mvnw.cmd spring-boot:run
```

macOS / Linux 用 `./mvnw spring-boot:run`。

启动后访问 <http://localhost:8080/api/ai/ping>，返回 `{"code":0,"message":"ok","data":"pong"}` 说明服务已就绪。首次启动会在 `./data` 下创建 H2 数据库文件。

### 2. 启动前端（另开一个终端）

```powershell
cd frontend
pnpm install
pnpm dev
```

浏览器打开 <http://localhost:5173>。Vite 已配置代理，`/api` 请求自动转发到 `localhost:8080`。生产构建用 `pnpm build`，详细说明见 [frontend/README.md](frontend/README.md)。

### 3. 配置 AI API Key

```powershell
$env:AI_API_KEY="sk-你的key"
```

`application.properties` 中读取：

```properties
ai.base-url=https://api.deepseek.com
ai.api-key=${AI_API_KEY:}
ai.model=deepseek-flash
```

> ⚠️ 真实 Key 不得提交到仓库。`${AI_API_KEY:}` 的冒号表示兜底默认值：本地配了环境变量就用真实 Key，评审环境没配也能正常启动和构建。

### 4. 导入示例数据

后端启动后，在项目根目录执行：

```powershell
.\docs\import-sample.ps1
```

会导入 12 条 2026 年 11 月的示例账单，覆盖餐饮、交通、购物、居住、娱乐、医疗六个分类，合计 3167.5 元，方便直接查看统计图表。

## Docker 部署

一条命令构建并启动（前端已打包进镜像，不需要单独启 Node）：

```powershell
docker compose up --build
```

或手动：

```powershell
docker build -t gduf-bill .
docker run -p 8080:8080 -e AI_API_KEY=sk-你的key -v ${PWD}/data:/app/data gduf-bill
```

容器内应用监听 8080，浏览器访问 <http://localhost:8080>。`./data` 目录挂载到容器 `/app/data`，**容器删除后数据仍在宿主机**。

> 注意：本地 `mvnw spring-boot:run` 和 Docker 容器不要同时运行，否则会争抢 8080 端口和 H2 文件锁。

## 配置说明

`src/main/resources/application.properties` 关键配置：

```properties
server.port=8080

# 大模型
ai.base-url=https://api.deepseek.com
ai.api-key=${AI_API_KEY:}
ai.model=deepseek-flash

# 文件上传上限
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# 数据库：H2 文件模式
spring.datasource.url=jdbc:h2:file:./data/gduf-bill
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```

## 功能进度

| 功能 | 状态 | 负责人 |
| --- | --- | --- |
| Spring Boot 骨架 | ✅ 已完成 | — |
| 账单实体与 CRUD | ✅ 已完成 | 邱卓成 |
| AI 自然语言记账 | ✅ 已完成 | 钟嘉和 |
| 小票 OCR 识别 | ✅ 已完成 | 钟嘉和 |
| 自动分类与统计 | ✅ 已完成 | 陈宇亮 |
| 自然语言查账 | ✅ 已完成 | 钟嘉和 |
| 前端页面 | ✅ 已完成 | 麦苗 |
| Docker 部署 | ✅ 已完成 | 钟嘉和 |
| 预算与超支提醒 | ⬜ 未开始 | 待认领 |
| 答辩 PPT 与录屏 | 🚧 进行中 | 陈跃龙 |

## 接口一览

字段定义与错误码见 [docs/接口文档.md](docs/接口文档.md)。

| 方法 | 路径 | 说明 | 状态 |
| --- | --- | --- | --- |
| GET | `/api/ai/ping` | 健康检查 | ✅ |
| POST | `/api/ai/parse-bill` | 一句话 → 账单 JSON | ✅ |
| POST | `/api/ai/ocr-receipt` | 小票/截图 → 账单 JSON | ✅ |
| POST | `/api/ai/query` | 自然语言查账意图 | ✅ |
| POST | `/api/bill` | 新增账单 | ✅ |
| GET | `/api/bill` | 查询账单（按月 / 分类 / 关键词） | ✅ |
| DELETE | `/api/bill/{id}` | 删除账单 | ✅ |
| GET | `/api/stat/category` | 按类别统计 | ✅ |
| GET | `/api/stat/trend` | 月度趋势 | ✅ |
| GET | `/api/stat/range` | 按日期范围统计 | ✅ |

## 团队分工

| 学号 | 姓名 | 负责模块 |
| --- | --- | --- |
| 251731429 | 邱卓成 | 账单基础模块（实体、Repository、CRUD 接口） |
| 251735112 | 麦苗 | 前端页面、接口文档 |
| 251735331 | 陈宇亮 | 统计接口（分类 / 趋势 / 日期范围）与单元测试 |
| 251735254 | 陈跃龙 | README、项目文档、答辩 PPT、录屏 |
| 261731434 | 钟嘉和 | AI 调用（自然语言解析、小票识别、查账意图） |

## 已知问题与未完成部分

- 前端页面与后端接口已打通，但图表交互、异常提示等细节还可完善
- 小票识别精度依赖模型，模糊图片可能失败，需要用户手动修正
- 预算与超支提醒（MVP 第 4 项）尚未实现
- 数据库为 H2 文件模式，适合单机演示；多实例部署需换成 MySQL 或 PostgreSQL
- 暂未提供在线 Demo 地址

## 提交规范

- 每完成一个可运行的小功能提交一次，避免最后一次性上传
- commit message 用 `type: 说明`，例如 `feat: 新增账单新增接口`、`docs: 补充接口文档`
- 不提交 `target/`、`.idea/`、`data/`、`node_modules/`、API Key