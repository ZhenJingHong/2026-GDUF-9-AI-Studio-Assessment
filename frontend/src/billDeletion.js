import { shanghaiTime } from './billTime.js'

export function isDeleteRequest(text) {
  return /删除|删掉|删了|(?:把|将|帮我|请|想)\s*删|^删|(?:那笔|这笔|账单).{0,12}删|取消.{0,4}(?:那笔|这笔|刚才)/.test(text)
}

function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false
  const date = new Date(value)
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}

export function matchBillsForDeletion(rows, reference, text, plan, categories, now = Date.now()) {
  if (!plan?.deleteRequested || /(?:不要|别|不用|取消)\s*(?:删除|删掉|删了)|怎么删除|如何删除|能删除吗|(?:全部|所有|批量).{0,12}(?:删除|删掉)|(?:删除|删掉).{0,12}(?:全部|所有|批量)/.test(text)) {
    return { message: '请明确指定要删除的单笔账单。' }
  }

  // Explicit values take precedence over the model and any earlier bill reference.
  const ids = [...text.matchAll(/#([A-Za-z0-9-]+)/g)].map(m => m[1])
  const amounts = [...text.matchAll(/(?<![\d.\-])(\d+(?:\.\d+)?)\s*(?:元|块)/g)].map(m => Number(m[1]))
  const dates = [...text.matchAll(/\d{4}-\d{2}-\d{2}|前天|昨天|今天/g)].map(([value]) =>
    /^\d/.test(value) ? value : shanghaiTime(now - ({ 前天: 2, 昨天: 1, 今天: 0 }[value]) * 86400000).slice(0, 10))
  const namedCategories = categories.filter(c => text.includes(c))
  if ([ids, amounts, namedCategories].some(values => new Set(values).size > 1) || /-\d+(?:\.\d+)?\s*(?:元|块)/.test(text)) {
    return { message: '描述里有多组条件，请一次指定一笔账单。' }
  }

  const id = ids[0]
  const amount = amounts[0] ?? plan.amount
  const date = new Set(dates).size === 1 && !/(?:从|自|以来|起|之前|之后|截至|以前|以后)/.test(text) ? dates[0] : null
  const start = date ?? plan.start
  const end = date ?? plan.end
  const category = namedCategories[0] ?? plan.category
  const merchant = plan.merchant?.trim()
  const clock = text.match(/(\d{1,2})[:：]([0-5]\d)/)
  const time = clock ? clock[1].padStart(2, '0') + ':' + clock[2] : plan.time
  const cents = amount == null ? null : Math.round(amount * 100)

  if ((amount != null && (typeof amount !== 'number' || !Number.isFinite(amount)
      || amount <= 0 || amount > 9999999999.99 || Math.abs(amount * 100 - cents) > 0.001))
      || (start != null && !validDate(start)) || (end != null && !validDate(end)) || (start && end && start > end)
      || (category != null && !categories.includes(category)) || (time != null && !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(time))
      || (new Set(dates).size > 1 && !(start && end && dates.every(d => d >= start && d <= end)))) {
    return { message: '筛选条件有歧义，请给出明确的金额、日期或账单编号。' }
  }

  const hasFilter = id || cents != null || start || end || category || merchant || time
  const useReference = !hasFilter && (plan.useReference || /刚才|刚刚|上一笔|这笔|那笔/.test(text))
  if (!hasFilter && (!useReference || !reference.length)) {
    return { message: '请补充账单的金额、日期、类别、商户或编号。' }
  }
  const matches = rows.filter(bill => {
    const day = bill.occurredAt?.slice(0, 10) || ''
    return (!id || id === String(bill.id))
      && (cents == null || cents === Math.round(Number(bill.amount) * 100))
      && (!(start || end) || (validDate(day) && (!start || day >= start) && (!end || day <= end)))
      && (!time || (bill.timePrecision !== 'DATE' && bill.occurredAt?.slice(11, 16) === time
        && (bill.timePrecision === 'EXACT' || time !== '00:00')))
      && (!category || bill.category === category)
      && (!merchant || bill.merchant?.toLowerCase().includes(merchant.toLowerCase()))
      && (!useReference || reference.includes(String(bill.id)))
  })
  return matches.length ? { matches } : { message: '没有找到同时符合这些条件的单笔账单。可以换个条件再找。' }
}

export function billSnapshotMatches(a, b) {
  return Boolean(a && b && String(a.id) === String(b.id) && Number(a.amount) === Number(b.amount)
    && ['occurredAt', 'category', 'merchant', 'note', 'createdAt', 'timePrecision']
      .every(key => (a[key] ?? null) === (b[key] ?? null)))
}
