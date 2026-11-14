// 无第三方依赖的本地前端运行器：同时提供静态页面和后端代理。
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { dirname, resolve, extname, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = resolve(dirname(fileURLToPath(import.meta.url)), 'dist')
const backend = new URL(process.env.API_TARGET || 'http://127.0.0.1:8080')
const port = Number(process.env.PORT || 4173)
const types = { '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.svg':'image/svg+xml', '.png':'image/png', '.ico':'image/x-icon' }
http.createServer(async (req,res) => {
  try {
    const url = new URL(req.url, 'http://localhost')
    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) {
      const target = new URL(backend); target.pathname=url.pathname; target.search=url.search
      const upstream=http.request(target,{method:req.method,headers:{...req.headers,host:target.host}},reply=>{res.writeHead(reply.statusCode,reply.headers);reply.pipe(res)})
      upstream.on('error',()=>{if(!res.headersSent)res.writeHead(502,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({code:50001,message:'无法连接 Spring Boot 后端，请确认后端已在 8080 端口启动',data:null}))})
      upstream.setTimeout(60000,()=>upstream.destroy(new Error('timeout')))
      req.on('aborted',()=>upstream.destroy());req.pipe(upstream);return
    }
    const file=resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname))
    if(!file.startsWith(root+sep)){res.writeHead(403);res.end();return}
    const data=await readFile(file)
    res.writeHead(200,{'Content-Type':types[extname(file)]||'application/octet-stream','Cache-Control':'no-cache'});res.end(data)
  } catch {res.writeHead(404);res.end('Not found')}
}).listen(port,'127.0.0.1',()=>console.log(`小账前端：http://127.0.0.1:${port}\n后端代理：${backend.origin}\n请保持此窗口打开。`))
