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
  updateAmount: (id, amount) => request(`/bill/${encodeURIComponent(id)}/amount`, { method: 'PUT', body: { amount } }),
  budget: month => request('/budget', { query: { month } }),
  saveBudget: body => request('/budget', { method: 'POST', body }),
  bills: query => request('/bill', { query }),
  add: body => request('/bill', { method: 'POST', body }),
  remove: id => request(`/bill/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  deleteFilter: text => request('/ai/chat/delete-filter', { method: 'POST', body: { text } }),
  removeConfirmed: ({ id, amount, occurredAt, category, merchant, note, createdAt, timePrecision }) =>
    request(`/bill/${encodeURIComponent(id)}/confirmed`, { method: 'DELETE',
      body: { amount, occurredAt, category, merchant, note, createdAt, timePrecision } }),
  category: month => request('/stat/category', { query: { month } }),
  trend: (from, to) => request('/stat/trend', { query: { from, to } }),
  range: query => request('/stat/range', { query }),
  advice: stats => request('/ai/advice', { method: 'POST', body: { stats } }),
  chat: body => request('/ai/chat', { method: 'POST', body }),
  chatRecord: body => request('/ai/chat/records', { method: 'POST', body }),
  budgetDefaults: () => request('/budget/defaults'),
  setBudgetDefaults: budgets => request('/budget/defaults', { method: 'PUT', body: { budgets } }),
  budgetMonth: month => request('/budget/month', { query: { month } }),
  setBudgetMonth: (month, budgets) => request('/budget/month', { method: 'PUT', query: { month }, body: { budgets } }),
  restoreBudgetMonth: month => request('/budget/month', { method: 'DELETE', query: { month } }),
  ocr: file => { const body = new FormData(); body.append('file', file); return request('/ai/ocr-receipt', { method: 'POST', body }) },
}
