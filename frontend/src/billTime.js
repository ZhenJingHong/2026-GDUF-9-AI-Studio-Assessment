export function shanghaiTime(epoch = Date.now()) {
  return new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(new Date(epoch))
}
export function displayBillTime(bill) {
  const value=bill.occurredAt||''
  return bill.timePrecision==='DATE' || (!bill.timePrecision && value.endsWith('00:00:00')) ? value.slice(0,10)+'（时间未指定）' : value.slice(0,16)
}
export function normalizeTime(draft, request, previous) {
  if(!draft) return draft
  const text=request.text, explicitNow=/现在|刚刚|刚才|此刻/.test(text)
  const explicitClock=/(?:\d{1,2}\s*[:：点时]|[一二三四五六七八九十两]+点)/.test(text)
  const dateMention=/今天|昨天|前天|\d{4}-\d{2}-\d{2}|\d{1,2}月\d{1,2}/.test(text)
  if(explicitNow) return {...draft,occurredAt:shanghaiTime(request.sentAt),timePrecision:'EXACT'}
  if(!dateMention&&!explicitClock&&previous?.occurredAt) return {...draft,occurredAt:previous.occurredAt,timePrecision:previous.timePrecision||'DATE'}
  if(!explicitClock) return {...draft,occurredAt:(draft.occurredAt||shanghaiTime(request.sentAt)).slice(0,10)+' 00:00:00',timePrecision:'DATE'}
  return {...draft,timePrecision:'EXACT'}
}
