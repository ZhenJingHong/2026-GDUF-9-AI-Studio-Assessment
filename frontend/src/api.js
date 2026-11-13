const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')
export async function request(path, { method = 'GET', body, query } = {}) {
  const params = new URLSearchParams(Object.entries(query || {}).filter(([, v]) => v !== '' && v != null))
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 45000)
  try {
    const response = await fetch(`${base}${path}${params.size ? '?' + params : ''}`, {
      method, signal: controller.signal,
      headers: body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    })
    const result = await response.json()
    if (!response.ok) throw new Error(result.message || `接口请求失败（${response.status}）`)
    if (result.code !== 0) throw new Error(result.message || '服务处理失败')
    return result.data
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('请求超时，请稍后重试')
    throw error
  } finally { clearTimeout(timer) }
}
export const api = {
  bills: query => request('/bill', { query }),
  add: body => request('/bill', { method: 'POST', body }),
  remove: id => request(`/bill/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  category: month => request('/stat/category', { query: { month } }),
  trend: (from, to) => request('/stat/trend', { query: { from, to } }),
  range: query => request('/stat/range', { query }),
  parse: text => request('/ai/parse-bill', { method: 'POST', body: { text } }),
  query: text => request('/ai/query', { method: 'POST', body: { text } }),
  ocr: file => { const body = new FormData(); body.append('file', file); return request('/ai/ocr-receipt', { method: 'POST', body }) },
}
