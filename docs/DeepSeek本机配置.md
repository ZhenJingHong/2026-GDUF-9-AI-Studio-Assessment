# DeepSeek 本机配置

项目现有 AiClient 已通过 https://api.deepseek.com 的 OpenAI 兼容接口调用 deepseek-flash。文字记账、小票识别和查询意图共用此客户端。

1. 在 https://platform.deepseek.com/ 申请自己的 API Key，确认账户额度可用。不要提交 Key 到仓库或聊天。
2. 停止旧的 Java 后端（保留 Nginx）。在项目根目录的 PowerShell 中执行 `./启动后端-DeepSeek.ps1`，在隐藏输入提示中填写 Key。
3. 保持终端开启，浏览器访问 http://localhost ，使用后端模式，在 AI 记账框输入「午饭25元，在饭堂」，发送后核对草稿，再确认保存。

此脚本不把 Key 写入文件或系统环境变量，只传给当前后端进程。重新运行脚本需重新输入。若终端执行策略不允许脚本，不必修改系统策略，可在自己的终端设置 AI_API_KEY 后执行 mvnw.cmd spring-boot:run。

也支持 DEEPSEEK_API_KEY 环境变量；两者同时存在时 AI_API_KEY 优先。AI_BASE_URL、AI_MODEL 可覆盖地址和模型。Docker Compose 仍使用 AI_API_KEY，将其设置在启动 docker compose 的终端中，并重新创建容器以加载配置。

没有有效 Key 时，手动记账和预算仍可用；AI 操作返回明确配置提示。有效 Key 及账户授权/额度必须由使用者准备，代码接入不代表付费模型请求已实际成功。
