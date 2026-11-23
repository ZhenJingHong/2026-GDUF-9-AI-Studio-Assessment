import { shanghaiTime } from './billTime'

// An amount describing the old bill must match an individual bill, never a sum.
export function resolveBillChange(rows, session, text, now = Date.now()) {
  const target = text.match(/(?:改成|改为|改到|更正为)\s*(\d+(?:\.\d+)?)/)
  const amount = target ? Number(target[1]) : null
  const deleting = /删掉|删除|删了|取消.{0,4}(?:那笔|这笔|刚才)/.test(text) && !target
  if (!deleting && (!target || !(amount > 0) || amount > 9999999999.99 || !/^\d+(?:\.\d{1,2})?$/.test(target[1]))) {
    return { error: '请说明新的金额，例如“刚才那笔改成40元”，或说“删除刚才那笔”。' }
  }
  const prefix = target ? text.slice(0, target.index) : text
  const oldAmount = prefix.replace(/\d{4}-\d{2}-\d{2}/g, '').replace(/#[A-Za-z0-9-]+/g, '')
    .match(/(?:的|那笔|这笔|金额|把|将|元|块|\s|^)(\d+(?:\.\d{1,2})?)(?:元|块|那笔|这笔|删|\s|$)/)?.[1]
  const id = text.match(/#([A-Za-z0-9-]+)/)?.[1]
  const offset = text.includes('前天') ? 2 : text.includes('昨天') ? 1 : 0
  const day = /昨天|前天|今天/.test(text) ? shanghaiTime(now - offset * 86400000).slice(0, 10) : text.match(/\d{4}-\d{2}-\d{2}/)?.[0]
  let matches = rows.filter(b => (!id || String(b.id) === id) && (!day || b.occurredAt?.startsWith(day))
    && (oldAmount == null || Math.round(Number(b.amount) * 100) === Math.round(Number(oldAmount) * 100)))
  const merchants = [...new Set(rows.map(b => b.merchant).filter(m => m && text.includes(m)))]
  if (merchants.length) matches = matches.filter(b => merchants.includes(b.merchant))
  if (!id && !day && oldAmount == null && !merchants.length) {
    // Last reference can be a single bill or a query containing several bills.
    let ids = session.billReference
    if (!Array.isArray(ids)) {
      const last = [...session.messages].reverse().find(m => m.role === 'assistant' && /#([A-Za-z0-9-]+)/.test(m.content))
      ids = last ? [...last.content.matchAll(/#([A-Za-z0-9-]+)/g)].map(m => m[1]) : []
    }
    matches = matches.filter(b => ids.includes(String(b.id)))
  }
  if (matches.length !== 1) return { error: matches.length
    ? '这里有多笔账单，请补充金额、商户或账单编号，说明要处理哪一笔。'
    : '没有找到符合描述的单笔账单。查询里的合计可能由多笔组成，不能当作一笔账单修改或删除。请补充原账单金额或编号。' }
  return { bill: matches[0], amount, deleting }
}
