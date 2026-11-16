# Nginx + Spring Boot 部署

访问入口为 `http://localhost:80`。Nginx 提供构建后的前端，保留 `/api/` 路径代理至 `http://127.0.0.1:8080`。本配置同时监听 IPv4/IPv6 本机地址，不对局域网开放。

## 组装 Windows 前端运行包

1. 在 frontend 目录安装依赖并执行 `npm run build` 或 `pnpm build`。
2. 从 https://nginx.org/en/download.html 下载并解压 Windows 版 Nginx。本次使用 1.30.6。
3. 将 `frontend/dist` 中的内容放进 Nginx 的 `html` 目录，替换默认首页。
4. 用本目录的 `nginx.conf` 替换 Nginx 的 `conf/nginx.conf`。
5. 将「启动Nginx.cmd」「停止Nginx.cmd」放在 `nginx.exe` 同一目录，保留原有 `conf/mime.types` 和 `logs` 目录。
6. 启动团队 Spring Boot 后端（8080），再双击「启动Nginx.cmd」，浏览器访问 `http://localhost`。

运行前端无需 Node.js；重新构建源码时才需要。不要直接双击 dist/index.html。80 端口已有服务时先处理端口冲突，避免重复启动。后端不在 8080 时修改 proxy_pass。配置支持 10MB 上传及 120 秒代理读取超时。外网正式发布还需另外配置域名、HTTPS 和访问控制。

Nginx 程序和 dist 不提交源码仓库，按上面步骤生成运行包。Java 后端独立启动；Docker 部署方式不受本配置影响。

## 本次实际验证

真实 Spring Boot + 独立 H2 文件数据库验证了：保存 2099-11 总预算 2000 元、刷新读取、支出 2100 元后显示超支 100 元，以及网页新增/删除账单后重新计算。未调用真实 AI/OCR 模型。详情见本目录「联调结果.md」。
