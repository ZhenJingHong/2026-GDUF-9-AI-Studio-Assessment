import { screenBoundary } from './boundary'

export function demoPlan({ text, pending }, categories, month) {
  // 演示模式不请求后端，越界请求在本地按同一套规则拒答
  const refused = screenBoundary(text)
  if (refused) return { kind: 'REFUSE', pending, message: refused.reply }
  const today = new Date()
  const local = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' })
  function findDate(input) {
    const relative = /前天|昨天|今天/.exec(input)?.[0]
    return relative ? local.format(new Date(today.getTime() - ({ 前天: 2, 昨天: 1, 今天: 0 }[relative]) * 86400000))
      : input.match(/\b\d{4}-\d{2}-\d{2}\b/)?.[0]
  }
  const [recordedText, queryText] = text.split(/再查|然后查/, 2)
  const withoutDate = recordedText.replace(/\d{4}-\d{2}-\d{2}/g, '')
  const amounts = [...withoutDate.matchAll(/(\d+(?:\.\d+)?)\s*(?:元|块)/g)]
  const asks = queryText != null || /查|多少|明细|几笔|有哪些/.test(text)
  const records = queryText != null || /记|花了|付了|买了/.test(recordedText) || (!asks && amounts.length > 0)
  const queryDay = findDate(queryText ?? text)
  const day = findDate(recordedText)
  const lastDay = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5)), 0)).toISOString().slice(0, 10)
  const query = asks ? {
    action: /明细|有哪些/.test(text) ? 'list' : 'stat',
    start: queryDay || `${month}-01`, end: queryDay || lastDay,
    category: categories.find(category => (queryText ?? text).includes(category)) || null,
    aggregate: /几笔/.test(text) ? 'count' : 'sum',
  } : pending?.query
  if (/假如|假设|如果|例如|能不能|可以.*吗/.test(text) || amounts.length > 1
      || (!asks && !records && !pending)) {
    return { kind: 'UNSUPPORTED', pending, message: '请描述一笔实际消费（多笔请分开发送），或查询消费。' }
  }
  if (asks && !records && !pending) return { kind: 'QUERY', query }
  const category = categories.find(category => recordedText.includes(category))
    || (/地铁|打车|公交/.test(recordedText) ? '交通' : /饭|餐|咖啡|奶茶/.test(recordedText) ? '餐饮' : null)
  const draft = { ...pending?.draft }
  const amountText = amounts[0]?.[1] || withoutDate.match(/^\s*(\d+(?:\.\d+)?)\s*$/)?.[1]
  if (amountText && Number(amountText) > 0 && Number(amountText) <= 9999999999.99 && /^\d+(?:\.\d{1,2}0*)?$/.test(amountText)) draft.amount = Number(amountText)
  if (category) draft.category = category
  if (day && !Number.isNaN(Date.parse(day)) && new Date(day).toISOString().slice(0, 10) === day) {
    draft.occurredAt = day + ' 00:00:00'
  }
  draft.note ||= recordedText.slice(0, 255)
  const missing = [!draft.amount && '金额', !draft.category && '分类', !draft.occurredAt && '消费日期'].filter(Boolean)
  return {
    kind: missing.length ? 'CLARIFY' : query ? 'RECORD_THEN_QUERY' : 'RECORD',
    draft, query, pending: { source: 'TEXT', draft, query },
    message: missing.length ? `请补充${missing.join('、')}。` : null,
  }
}
