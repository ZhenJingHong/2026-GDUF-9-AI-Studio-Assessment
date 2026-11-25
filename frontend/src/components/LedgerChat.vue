<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Send, Upload, Trash2, Plus } from 'lucide-vue-next'
import { api } from '../api'
import { screenBoundary } from '../boundary'
import { demoPlan } from '../chatDemo'
import { resolveBillChange } from '../billChange'
import { billSnapshotMatches, isDeleteRequest, matchBillsForDeletion } from '../billDeletion'
import { displayBillTime, normalizeTime, shanghaiTime } from '../billTime'

const props = defineProps({
  mode: String, month: String, categories: Array, money: Function, demoBills: Function, saveDemo: Function,
})
const emit = defineEmits(['recorded'])
const key = 'pocket-conversations-v1'
let stored
try { stored = JSON.parse(localStorage.getItem(key)) } catch { stored = null }
const conversations = ref(Array.isArray(stored?.conversations) ? stored.conversations : [])
const selected = ref(stored?.selected || {})
const storageError = ref('')
const jobs = reactive(new Set())
const historyOpen = ref(false)
const deletions = reactive(new Map())
const deletion = computed(() => deletions.get(current.value?.id))
const input = ref(null)
const transcript = ref(null)
const current = computed(() => conversations.value.find(item => item.id === selected.value[props.mode]))
const visibleHistory = computed(() => conversations.value.filter(item => item.mode === props.mode))

function persist() {
  try {
    localStorage.setItem(key, JSON.stringify({ conversations: conversations.value, selected: selected.value }))
    storageError.value = ''
  } catch {
    storageError.value = '浏览器存储不可用，刷新页面可能丢失会话。'
  }
}

function newChat() {
  const id = crypto.randomUUID()
  conversations.value.unshift({ id, mode: props.mode, messages: [], pending: null, input: '' })
  select(id)
}

function select(id) {
  selected.value[props.mode] = id
  historyOpen.value = false
}

function deleteChat(session) {
  deletions.delete(session.id)
  conversations.value = conversations.value.filter(item => item.id !== session.id)
  if (current.value) return
  if (visibleHistory.value.length) select(visibleHistory.value[0].id)
  else newChat()
}

watch(() => props.mode, () => {
  if (current.value) return
  if (visibleHistory.value.length) select(visibleHistory.value[0].id)
  else newChat()
}, { immediate: true })

watch([conversations, selected], persist, { deep: true })
watch(() => [current.value?.id, current.value?.messages.length], async () => {
  await nextTick()
  if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
})

async function focus() {
  await nextTick()
  input.value?.focus()
}
defineExpose({ focus })

function addMessage(session, role, content, kind) {
  session.messages.push(kind ? { role, content, kind } : { role, content })
}

function saveAction(session, draft, query) {
  session.action = {
    stage: 'save', actionId: crypto.randomUUID(), query,
    bill: {
      amount: draft.amount, category: draft.category, occurredAt: draft.occurredAt, timePrecision: draft.timePrecision,
      merchant: draft.merchant || null, note: draft.note || null,
    },
  }
}

function complete(draft) {
  if (!draft || !(draft.amount > 0) || draft.amount > 9999999999.99
      || !/^\d+(?:\.\d{1,2}0*)?$/.test(String(draft.amount))
      || !props.categories.includes(draft.category)) return false
  const time = draft.occurredAt
  if (!time || !/^\d{4}-\d{2}-\d{2} [0-2]\d:[0-5]\d:[0-5]\d$/.test(time)
      || time.startsWith('0000') || Number(time.slice(11, 13)) > 23) return false
  const date = new Date(time.slice(0, 10))
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === time.slice(0, 10)
}

async function allBills(query = {}) {
  const size = 50
  const bills = new Map()
  for (let page = 1; ; page++) {
    const batch = await api.bills({ ...query, page, size })
    const count = bills.size
    for (const bill of batch) bills.set(bill.id, bill)
    if (batch.length < size || bills.size === count) return [...bills.values()]
  }
}

async function queryLedger(session, spec) {
  const demo = session.mode === 'demo'
  const { start, end, category, keyword, aggregate = 'sum' } = spec
  const label = `${demo ? '演示账本 · ' : ''}${start} 至 ${end} · ${category || '全部分类'}`
  const rows = demo ? props.demoBills() : await allBills({ category, keyword })
  const selected = rows.filter(bill => {
    const date = bill.occurredAt?.slice(0, 10)
    return date >= start && date <= end && (!category || bill.category === category)
      && (!keyword || !demo || `${bill.merchant || ''}${bill.note || ''}`.includes(keyword))
  })
  session.billReference = selected.map(b => String(b.id))
  if (spec.action === 'list') {
    const lines = selected.map(bill => `#${bill.id} · ${bill.occurredAt.slice(0, 10)} · ${bill.merchant || bill.category} · ¥${props.money(bill.amount)}`)
    return [`${label}，共 ${selected.length} 笔`, ...lines].join('\n')
  }
  if (aggregate === 'count') return `${label}\n账单数量：${selected.length} 笔`
  const amounts = selected.map(bill => Math.round(bill.amount * 100))
  const sum = amounts.reduce((total, value) => total + value, 0)
  const cents = aggregate === 'max' ? amounts.reduce((max, value) => Math.max(max, value), 0)
    : aggregate === 'avg' ? sum / amounts.length : sum
  return `${label}\n${{ sum: '支出合计', max: '最大单笔', avg: '平均消费' }[aggregate] || '支出合计'}：${selected.length ? '¥' + props.money(cents / 100) : '¥0.00'}`
}

async function changeBill(session, text) {
  const rows = session.mode === 'demo' ? props.demoBills() : await allBills()
  const change = resolveBillChange(rows, session, text)
  if (change.error || change.deleting) {
    addMessage(session, 'assistant', change.error || '请单独发送删除请求，找到账单后再确认删除。')
    return
  }
  const { bill, amount } = change
  const oldAmount = bill.amount
  if (session.mode === 'demo') {
    bill.amount = amount
    localStorage.setItem('pocket-bills', JSON.stringify(rows))
  } else await api.updateAmount(bill.id, amount)
  session.billReference = [String(bill.id)]
  addMessage(session, 'assistant', `已更正原账单 #${bill.id}：¥${props.money(oldAmount)} → ¥${props.money(amount)}，没有新增账单。`)
  emit('recorded', session.mode)
}

async function prepareDeletion(session, text) {
  const demo = session.mode === 'demo'
  const rows = demo ? props.demoBills() : await allBills()
  const plan = demo
    ? { deleteRequested: true, merchant: rows.find(b => b.merchant && text.includes(b.merchant))?.merchant }
    : await api.deleteFilter(text)
  const { message, matches } = matchBillsForDeletion(rows, session.billReference || [], text, plan, props.categories)
  if (message) {
    addMessage(session, 'assistant', message)
    return
  }
  deletions.set(session.id, { matches, selectedId: matches.length === 1 ? matches[0].id : null })
}

function cancelDeletion() {
  const session = current.value
  if (jobs.has(session.id)) return
  deletions.delete(session.id)
  addMessage(session, 'assistant', '已取消删除。')
}

function confirmDeletion() {
  const session = current.value
  const pending = deletions.get(session.id)
  const bill = pending?.matches.find(candidate => candidate.id === pending.selectedId)
  if (!bill) return
  run(session, async () => {
    if (session.mode === 'demo') {
      const rows = props.demoBills()
      if (!billSnapshotMatches(bill, rows.find(row => row.id === bill.id))) {
        deletions.delete(session.id)
        throw new Error('账单已变化或不存在，请重新查找再确认')
      }
      localStorage.setItem('pocket-bills', JSON.stringify(rows.filter(row => row.id !== bill.id)))
    } else await api.removeConfirmed(bill)
    deletions.delete(session.id)
    session.billReference = (session.billReference || []).filter(id => id !== String(bill.id))
    addMessage(session, 'assistant', `已删除账单 #${bill.id}：${displayBillTime(bill)} · ¥${props.money(bill.amount)}。`)
    emit('recorded', session.mode)
  })
}

async function runAction(session) {
  if (session.action?.stage === 'parse') {
    const { request, month } = session.action
    const apiRequest = {text:request.text,history:request.history,pending:request.pending ? {...request.pending,draft:request.pending.draft ? Object.fromEntries(Object.entries(request.pending.draft).filter(([k])=>k!=='timePrecision')) : null} : null}
    const plan = session.mode === 'demo' ? demoPlan(request, props.categories, month) : await api.chat(apiRequest)
    if(plan.draft) plan.draft=normalizeTime(plan.draft,request,request.pending?.draft)
    if(plan.pending?.draft) plan.pending.draft=plan.draft || normalizeTime(plan.pending.draft,request,request.pending?.draft)
    session.action = null
    session.pending = plan.pending || null
    if (plan.kind === 'RECORD' || plan.kind === 'RECORD_THEN_QUERY') {
      saveAction(session, plan.draft, plan.query)
    } else if (plan.kind === 'QUERY') {
      session.action = { stage: 'query', query: plan.query }
    } else {
      addMessage(session, 'assistant', plan.message || '请补充消费信息。', plan.kind === 'REFUSE' ? 'REFUSE' : null)
    }
  }
  if (session.action?.stage === 'save') {
    const { actionId, bill, query } = session.action
    persist()
    const receipt = session.mode === 'demo' ? props.saveDemo(actionId, bill) : await api.chatRecord({ actionId, bill })
    session.pending = null
    session.billReference = [String(receipt.billId)]
    session.action = query ? { stage: 'query', query } : null
    addMessage(session, 'assistant', `${session.mode === 'demo' ? '演示账本' : '已入账'} · #${receipt.billId}\n${displayBillTime(bill)} · ${bill.category} · ¥${props.money(bill.amount)}`)
    persist()
    emit('recorded', session.mode)
  }
  if (session.action?.stage === 'query') {
    const result = await queryLedger(session, session.action.query)
    session.action = null
    addMessage(session, 'assistant', result)
  }
}

async function run(session, task) {
  if (jobs.has(session.id)) return
  jobs.add(session.id)
  session.error = ''
  try {
    await task()
  } catch (error) {
    session.error = error.message || '处理失败，请重试'
  } finally {
    jobs.delete(session.id)
    persist()
  }
}

function submit() {
  const session = current.value
  const text = session.input.trim()
  if (!text || session.action || deletion.value || jobs.has(session.id)) return
  // 边界拒答优先于「删除」「改金额」等快捷通道：否则「删除所有账单」会走进单笔删除流程
  const refused = screenBoundary(text)
  if (refused) {
    session.input = ''
    addMessage(session, 'user', text)
    addMessage(session, 'assistant', refused.reply, 'REFUSE')
    persist()
    return
  }
  if (isDeleteRequest(text)) {
    session.input = ''
    addMessage(session, 'user', text)
    if (/改成|改为|改到|更正为/.test(text)) {
      addMessage(session, 'assistant', '这句话同时提到了删除和改金额。请单独说明你要删除，还是修改金额。')
    } else run(session, () => prepareDeletion(session, text))
    return
  }
  if (/改|更正|记错|不是/.test(text) && !session.pending) {
    session.input=''; addMessage(session,'user',text)
    run(session,()=>changeBill(session,text)); return
  }
  const userMessages = session.messages.flatMap((message, index) => message.role === 'user' ? [index] : [])
  const start = userMessages.at(-6) ?? 0
  const history = session.messages.slice(start).map(({ role, content }) => ({ role, content }))
  session.action = { stage: 'parse', month: props.month, request: { text, history, pending: session.pending, sentAt: Date.now() } }
  session.input = ''
  addMessage(session, 'user', text)
  run(session, () => runAction(session))
}

function retry() {
  const session = current.value
  run(session, () => runAction(session))
}

function confirmReceipt() {
  const session = current.value
  if (session.action || jobs.has(session.id) || !complete(session.pending?.draft)) return
  saveAction(session, session.pending.draft, session.pending.query)
  run(session, () => runAction(session))
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  const session = current.value
  if (!file || session.action || session.pending || jobs.has(session.id)) return
  if (session.mode === 'demo') {
    session.error = '小票识别需要在账本设置中连接后端。'
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    session.error = '请选择 10 MB 以内的 JPG、PNG 或 WebP 图片。'
    return
  }
  addMessage(session, 'user', '识别小票')
  await run(session, async () => {
    const draft = await api.ocr(file)
    if (draft.occurredAt?.length === 10) draft.occurredAt += ' 00:00:00'
    if (draft.occurredAt) draft.occurredAt = draft.occurredAt.replace('T', ' ')
    session.pending = { source: 'RECEIPT', draft, query: null }
    addMessage(session, 'assistant', complete(draft) ? '请核对小票，确认后入账。' : '请回复补充或更正小票信息。')
  })
}
</script>

<template>
  <section v-if="current" class="card ledger-chat">
    <button class="secondary chat-history-toggle" type="button" :aria-expanded="historyOpen"
      @click="historyOpen = !historyOpen">会话历史</button>
    <aside class="chat-history" :class="{ 'chat-history-open': historyOpen }" aria-label="会话历史">
      <button class="secondary" type="button" @click="newChat"><Plus :size="16"/>新建会话</button>
      <div v-for="session in visibleHistory" :key="session.id" class="chat-history-item">
        <button class="chat-select" :aria-current="session.id === current.id ? 'true' : undefined"
          @click="select(session.id)">{{ session.messages[0]?.content || '新会话' }}</button>
        <button class="icon" type="button" aria-label="删除会话" :disabled="jobs.has(session.id)"
          @click="deleteChat(session)"><Trash2 :size="15"/></button>
      </div>
    </aside>
    <div class="chat-main">
      <div ref="transcript" class="transcript" role="log" aria-label="对话">
        <p v-if="!current.messages.length" class="empty">
          {{ mode === 'demo' ? '本地演示 · ' : '' }}试试「昨天午饭花了 25 元」或「这个月花了多少」。
        </p>
        <div v-for="(message, index) in current.messages" :key="index" class="chat-message"
          :class="['chat-' + message.role, { 'chat-boundary': message.kind === 'REFUSE' }]"><span
            v-if="message.kind === 'REFUSE'" class="boundary-tag">边界提示 · 未执行</span>{{ message.content.replace(/(\d{4}-\d{2}-\d{2}) 00:00:00/g,'$1（时间未指定）') }}</div>
        <div v-if="current.pending?.draft" class="chat-draft">
          <strong>{{ current.pending.source === 'RECEIPT' ? '小票账单' : '待补充账单' }}</strong>
          <p>{{ current.pending.draft.amount == null ? '金额待补' : '¥' + money(current.pending.draft.amount) }}
            · {{ current.pending.draft.category || '分类待补' }} · {{ current.pending.draft.occurredAt || '日期待补' }}</p>
          <p v-if="current.pending.draft.merchant || current.pending.draft.note">
            {{ [current.pending.draft.merchant, current.pending.draft.note].filter(Boolean).join(' · ') }}
          </p>
          <button v-if="current.pending.source === 'RECEIPT' && complete(current.pending.draft)" class="primary"
            type="button" :disabled="jobs.has(current.id) || !!current.action || !!deletion" @click="confirmReceipt">确认入账</button>
        </div>
      </div>
      <fieldset v-if="deletion" class="chat-draft delete-preview" :disabled="jobs.has(current.id)">
        <legend>{{ deletion.matches.length === 1 ? '确认要删除的账单' : '找到 ' + deletion.matches.length + ' 笔账单，请选择一笔' }}</legend>
        <div class="delete-candidates">
        <div v-for="bill in deletion.matches" :key="bill.id">
          <label>
            <input v-if="deletion.matches.length > 1" v-model="deletion.selectedId" type="radio"
              :name="'delete-bill-' + current.id" :value="bill.id">
            #{{ bill.id }} · {{ displayBillTime(bill) }} · {{ bill.category }} · ¥{{ money(bill.amount) }}
            <span v-if="bill.merchant"> · {{ bill.merchant }}</span>
            <span v-if="bill.note"> · {{ bill.note }}</span>
          </label>
        </div>
        </div>
        <div class="delete-actions">
          <button class="secondary" type="button" @click="cancelDeletion">取消</button>
          <button class="primary" type="button" :disabled="deletion.selectedId == null" @click="confirmDeletion">确认删除</button>
        </div>
      </fieldset>
      <p v-if="current.error || storageError" class="budget-error" role="alert">{{ current.error || storageError }}</p>
      <p v-if="jobs.has(current.id)" role="status">正在处理…</p>
      <button v-else-if="current.action" class="secondary" type="button" @click="retry">
        {{ { parse: '重试解析', save: '重试入账', query: '重试查询' }[current.action.stage] }}
      </button>
      <form @submit.prevent="submit">
        <div class="ai-input">
          <input ref="input" v-model="current.input" maxlength="4000" placeholder="说说你想记的消费，或问问账本"
            aria-label="AI 输入" :disabled="jobs.has(current.id) || !!current.action || !!deletion">
          <button class="send" :disabled="!current.input.trim() || jobs.has(current.id) || !!current.action || !!deletion"
            aria-label="发送"><Send :size="19"/></button>
        </div>
        <div class="chat-actions">
          <label class="upload"><Upload :size="15"/>识别小票
            <input type="file" accept="image/jpeg,image/png,image/webp"
              :disabled="jobs.has(current.id) || !!current.action || !!current.pending || !!deletion" @change="upload">
          </label>
          <button v-if="current.pending && !current.action" class="text-button" type="button"
            :disabled="jobs.has(current.id)" @click="current.pending = null">取消待办</button>
        </div>
      </form>
    </div>
  </section>
</template>
