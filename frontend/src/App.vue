<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { Wallet, LayoutDashboard, ArrowUpRight, Plus, Search, Sparkles, SlidersHorizontal, ReceiptText, ChartNoAxesCombined, Settings2, ChevronRight, Trash2, Download, Leaf, Menu } from 'lucide-vue-next'
import Chart from './components/Chart.vue'
import { api } from './api'
import LedgerChat from './components/LedgerChat.vue'
import ManualBill from './components/ManualBill.vue'
import BudgetForm from './components/BudgetForm.vue'
import { budgetStore } from './budgets'
import { displayBillTime } from './billTime'
const categories = ['餐饮','交通','购物','娱乐','居住','医疗','教育','其他']
const colors = ['#34765b','#b6ce8b','#e8bb7b','#899cce','#d69385','#86bbbc','#c4b7d7','#c4c8c4']
const icons = ['🍜','🚕','🛍️','🎮','🏠','💊','📚','🧩']
function localDate(date = new Date()) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}` }
const currentMonth = localDate().slice(0,7)
// 使用新的设置键，让旧版浏览器中的演示模式不影响本次默认后端连接。
const month = ref(currentMonth), page = ref('总览'), mode = ref(localStorage.getItem('pocket-mode-v2') === 'demo' ? 'demo' : 'live'), mobile = ref(false)
const bills = ref([]), stats = ref(null), trend = ref(null), loading = ref(false), busy = ref(false), error = ref(''), toast = ref('')
const advice = ref(''), adviceStatus = ref(''), adviceLoading = ref(false)
const adviceStale = ref(false), advicePrompt = ref('')
const emptyAdviceMessage = '暂无消费记录，无法生成建议。'
const keyword = ref(''), category = ref(''), pendingDelete = ref(null)
const manual = ref(null)
function openManual() { manual.value = { mode:mode.value, month:month.value } }
function manualRecorded(source, change) { notify('这笔消费，记好啦'); recorded(source, change) }
const chat = ref(null), settingsMonth = ref(currentMonth), editorMonth = ref('')
const budgetSnapshot = ref(null), budgetError = ref(''), defaultsVersion = ref(0)
const budgetStatus = { NORMAL: '', NEAR: '接近预算', AT_LIMIT: '已用完', OVER: '超支' }
let loadVersion = 0, budgetVersion = 0

async function openChat() {
  page.value = 'AI 助手'
  mobile.value = false
  await nextTick()
  chat.value?.focus()
}

function demoBills() {
  const saved = localStorage.getItem('pocket-bills')
  return saved ? JSON.parse(saved) : seed()
}

function saveDemo(id, bill) {
  const rows = demoBills()
  if (!rows.some(row => row.id === id)) {
    localStorage.setItem('pocket-bills', JSON.stringify([...rows, { ...bill, id }]))
  }
  return { billId: id }
}

async function loadBudget() {
  const version = ++budgetVersion
  budgetError.value = ''
  budgetSnapshot.value = null
  try {
    const snapshot = await budgetStore.month(mode.value, month.value, mode.value === 'demo' ? demoBills() : [])
    if (version === budgetVersion) budgetSnapshot.value = snapshot
  } catch (e) {
    if (version === budgetVersion) budgetError.value = e.message || '预算加载失败'
  }
}

function budgetSaved(message) {
  notify(message)
  loadBudget()
}

function defaultsSaved(message) {
  defaultsVersion.value++
  budgetSaved(message)
}

function recorded(source, change) {
  if (mode.value !== source) return
  if (change?.month === month.value && (advice.value || adviceLoading.value)) {
    adviceStale.value = Boolean(advice.value)
    advicePrompt.value = change.type === 'added' ? '有新账单录入，是否重新生成？' : '账单有变动，是否重新生成？'
  }
  return load()
}
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function sixMonthsThrough(endingMonth) {
  const end = new Date(endingMonth + '-01T12:00:00')
  return Array.from({ length: 6 }, (_, index) =>
    localDate(new Date(end.getFullYear(), end.getMonth() + index - 5, 1)).slice(0, 7))
}

async function generateAdvice() {
  if (loading.value || adviceLoading.value) return
  const version = loadVersion
  const selectedMonth = month.value
  advicePrompt.value = ''
  adviceStatus.value = ''
  adviceLoading.value = true
  try {
    let result
    if (mode.value === 'demo') {
      const top = groups.value[0]
      if (top) {
        result = `「${top.category}」是所选月份支出最多的类别，共 ¥${money(top.total)}。可以先回顾这一类消费，看看哪些开销可以减少，再给下个月的同类支出设个预算。`
      }
    } else {
      const summary = await api.category(selectedMonth)
      if (version !== loadVersion) return
      if (summary.items.length) {
        result = await api.advice({ month: summary.month, total: summary.total, items: summary.items })
        if (typeof result !== 'string' || !result.trim()) throw new Error('建议为空')
      }
    }
    if (version !== loadVersion) return
    advice.value = result?.trim() || ''
    adviceStale.value = false
    if (!result) adviceStatus.value = emptyAdviceMessage
  } catch {
    if (version === loadVersion) adviceStatus.value = '暂无建议。请确认新版后端和 DeepSeek 已就绪，再试一次。'
  } finally {
    if (version === loadVersion) adviceLoading.value = false
  }
}

function seed() { return Array.from({length:36}, (_, i) => ({ id: i+1, amount: [28,18,128,36,25,68,52,19.9,239,42,12,88][i%12], category: categories[i%8], merchant: ['校园食堂','滴滴出行','生活好物','周末电影','日常生活','健康药房','书店','便利店'][i%8], note: ['午餐，认真吃饭','出门办事','给生活添一点新意','休息一下','日常开销','健康第一','学习投资','随手记录'][i%8], occurredAt: `${currentMonth}-${String(Math.max(1, new Date().getDate() - Math.floor(i/3))).padStart(2,'0')} ${String(10+i%10).padStart(2,'0')}:30:00` })) }
function notify(text) { toast.value = text; setTimeout(() => { if(toast.value === text) toast.value = '' }, 3500) }
async function load() {
  const version = ++loadVersion
  const selectedMonth = month.value
  const selectedMode = mode.value
  loading.value = true
  error.value = ''
  stats.value = null
  trend.value = null
  adviceStatus.value = ''
  adviceLoading.value = false
  loadBudget()
  try {
    const rows = selectedMode === 'demo' ? demoBills() : await api.bills({ month: selectedMonth })
    if (version !== loadVersion) return
    bills.value = rows
    if (selectedMode === 'live') {
      const [from] = sixMonthsThrough(selectedMonth)
      const results = await Promise.allSettled([api.category(selectedMonth), api.trend(from, selectedMonth)])
      if (version !== loadVersion) return
      if (results[0].status === 'fulfilled') stats.value = results[0].value
      if (results[1].status === 'fulfilled') trend.value = results[1].value
    }
  } catch (e) {
    if (version === loadVersion) {
      bills.value = []
      error.value = e.message || '连接失败'
    }
  } finally {
    if (version === loadVersion) loading.value = false
  }
}
watch(mode, () => {
  localStorage.setItem('pocket-mode-v2', mode.value)
  editorMonth.value = ''
  pendingDelete.value = null
})
watch([month, mode], () => {
  advice.value = ''
  adviceStale.value = false
  advicePrompt.value = ''
  load()
})
watch(page, () => { editorMonth.value = '' })
onMounted(load)
const monthly = computed(() => bills.value.filter(b => b.occurredAt?.startsWith(month.value)))
const filtered = computed(() => monthly.value.filter(b => (!category.value || b.category===category.value) && `${b.merchant||''}${b.note||''}`.toLowerCase().includes(keyword.value.toLowerCase())).sort((a,b)=>b.occurredAt.localeCompare(a.occurredAt)))
const total = computed(() => monthly.value.reduce((s,b)=>s+Math.round(Number(b.amount)*100),0)/100)
const groups = computed(() => stats.value?.items || categories.map(c=>({ category:c,total:monthly.value.filter(b=>b.category===c).reduce((s,b)=>s+Number(b.amount),0),count:monthly.value.filter(b=>b.category===c).length })).filter(x=>x.count).sort((a,b)=>b.total-a.total))
const noAdviceBills = computed(() => !loading.value && !error.value
  && (mode.value === 'live' && stats.value ? !stats.value.items.length : !monthly.value.length))
watch(noAdviceBills, empty => {
  if (empty) {
    advice.value = ''
    adviceStale.value = false
  }
})
const totalBudget = computed(() => budgetSnapshot.value?.budgets.find(item => item.category === null))
const categoryBudgets = computed(() => Object.fromEntries(
  (budgetSnapshot.value?.budgets || []).filter(item => item.category).map(item => [item.category, item])))
const alerts = computed(() => budgetSnapshot.value?.budgets.filter(item => item.status === 'OVER') || [])
const overviewMonths = computed(() => sixMonthsThrough(month.value))
const overviewTrend = computed(() => {
  if (mode.value === 'live') return trend.value
  const months = overviewMonths.value
  const totals = new Map(months.map(label => [label, 0]))
  for (const bill of bills.value) {
    const label = bill.occurredAt?.slice(0, 7)
    if (totals.has(label)) totals.set(label, totals.get(label) + Math.round(Number(bill.amount) * 100))
  }
  return { months, totals: months.map(label => totals.get(label) / 100) }
})
const overviewTrendLabel = computed(() => overviewTrend.value?.months.map((label, i) =>
  `${label} 支出 ¥${money(overviewTrend.value.totals[i])}`).join('，'))
const lineOption = computed(() => {
  let labels, values
  if (page.value === '总览' && overviewTrend.value) { labels = overviewTrend.value.months; values = overviewTrend.value.totals }
  else if(page.value==='消费分析' && trend.value) { labels=trend.value.months; values=trend.value.totals }
  else { const days = new Date(Number(month.value.slice(0,4)),Number(month.value.slice(5)),0).getDate(); labels=Array.from({length:days},(_,i)=>String(i+1)); values=labels.map(d=>monthly.value.filter(b=>Number(b.occurredAt.slice(8,10))===Number(d)).reduce((s,b)=>s+Number(b.amount),0)) }
  return { tooltip:{trigger:'axis',valueFormatter:v=>`¥${money(v)}`},grid:{left:45,right:18,top:24,bottom:25},xAxis:{type:'category',data:labels,axisLine:{show:false},axisTick:{show:false},axisLabel:{color:'#8b968f',interval:Math.max(0,Math.floor(labels.length/7)-1)}},yAxis:{type:'value',splitLine:{lineStyle:{color:'#edf0eb',type:'dashed'}},axisLabel:{color:'#8b968f'}},series:[{type:'line',smooth:true,data:values,symbol:'none',lineStyle:{color:'#34765b',width:3},areaStyle:{color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'#b7d7beaa'},{offset:1,color:'#ffffff00'}]}}}] }
})
const pieOption = computed(() => ({tooltip:{trigger:'item',formatter:'{b}: ¥{c} ({d}%)'},color:groups.value.map(x=>colors[categories.indexOf(x.category)]),series:[{type:'pie',radius:['65%','85%'],center:['50%','50%'],label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:5,borderRadius:6},data:groups.value.map(x=>({name:x.category,value:Number(x.total.toFixed(2))}))}]}))
async function remove() {
  if (busy.value) return
  const id = pendingDelete.value
  const source = mode.value
  const bill = bills.value.find(item => item.id === id)
  busy.value = true
  try {
    if (source === 'live') await api.remove(id)
    else localStorage.setItem('pocket-bills', JSON.stringify(demoBills().filter(bill => bill.id !== id)))
    pendingDelete.value = null
    await recorded(source, { type: 'deleted', month: bill?.occurredAt?.slice(0, 7) })
    notify('账单已删除')
  } catch (e) {
    notify(e.message)
  } finally {
    busy.value = false
  }
}
function exportCSV(){ const cell=v=>'"'+String(v??'').replaceAll('"','""').replace(/^[=+@-]/,"'$&")+'"';const csv='\uFEFF'+[['日期','分类','商户','金额','备注'],...filtered.value.map(b=>[b.occurredAt,b.category,b.merchant,b.amount,b.note])].map(r=>r.map(cell).join(',')).join('\r\n');const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`小账-${month.value}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000) }
</script>

<template>
  <div class="shell">
    <aside :class="['sidebar',{opened:mobile}]">
      <a class="brand" href="#" @click.prevent="page='总览'"><span class="brand-icon"><Wallet :size="25"/></span><span>小账<span class="brand-en">POCKET AI</span></span></a>
      <div class="workspace"><span class="avatar">我</span><div>我的生活账本<small>把日子，过得心中有数</small></div><ChevronRight :size="15"/></div>
      <p class="nav-label">我的工作台</p>
      <nav><button v-for="(item,i) in ['总览','账单明细','消费分析','AI 助手']" :class="{active:page===item}" @click="page=item;mobile=false"><component :is="[LayoutDashboard,ReceiptText,ChartNoAxesCombined,Sparkles][i]" :size="19"/>{{item}}<span v-if="i===3" class="tiny">AI</span></button></nav>
      <div class="sidebar-bottom"><div class="plant"><Leaf :size="24"/><h4>小小记录，慢慢变好。</h4><p>每一笔认真记录，<br>都让理想生活更近一步。</p></div><button class="settings" @click="page='设置';mobile=false"><Settings2 :size="18"/>账本设置</button><span class="version">POCKET · 为每一天留一点余裕</span></div>
    </aside>
    <main>
      <header><div class="breadcrumb"><button class="icon mobile-menu" aria-label="打开导航" @click="mobile=!mobile"><Menu/></button>我的工作台 <ChevronRight :size="13"/> <b>{{page}}</b></div><div class="header-right"><span :class="['status',{live:mode==='live'}]"><i></i>{{mode==='demo'?'本地演示模式':'后端连接模式'}}</span><span class="avatar small">我</span></div></header>
      <div class="content">
        <div class="title-row"><div><div class="eyebrow">YOUR MONEY, A LITTLE CLEARER</div><h1>{{page==='总览'?'生活有记录，花钱有主张。':page==='AI 助手'?'一句话，把账记明白。':page==='账单明细'?'每一笔，都有迹可循。':page==='消费分析'?'看见消费，找到生活的节奏。':'你的账本，你来定义。'}}</h1><p>{{page==='总览'?'欢迎回来，一起看看最近的钱都花在哪儿了。':'让记账成为一件轻松的小事。'}}</p></div><div class="entry-actions"><button class="secondary" @click="openChat()"><Sparkles :size="17"/>AI 对话记账</button><button class="primary" @click="openManual()"><Plus :size="18"/>手动记一笔</button></div></div>
        <div v-if="error" class="error" role="alert">{{error}}。请确认后端已启动，或在设置中切换本地演示。<button @click="load">重试</button></div>
        <LedgerChat v-show="page === 'AI 助手'" ref="chat" :mode="mode" :month="month"
          :categories="categories" :money="money" :demo-bills="demoBills" :save-demo="saveDemo" @recorded="recorded"/>
        <template v-if="page==='设置'">
          <section class="card settings-card">
            <h2>账本设置</h2>
            <label>数据来源<select v-model="mode"><option value="demo">本地演示</option><option value="live">连接 Spring Boot 后端</option></select></label>
          </section>
          <BudgetForm :key="mode + '-defaults'" :mode="mode" :categories="categories" :demo-bills="demoBills" @saved="defaultsSaved"/>
          <label class="budget-month-picker">选择预算月份<input class="month" type="month" v-model="settingsMonth"></label>
          <BudgetForm v-if="settingsMonth" :key="mode + settingsMonth" :mode="mode" :month="settingsMonth" :defaults-version="defaultsVersion"
            :categories="categories" :demo-bills="demoBills" @saved="budgetSaved"/>
        </template>
        <template v-else>
          <template v-if="page!=='AI 助手'">
            <section v-if="page === '总览'" class="card overview-advice" aria-labelledby="overview-advice-title">
              <div class="overview-advice-header">
                <div>
                  <h2 id="overview-advice-title"><Sparkles :size="19"/>消费建议</h2>
                  <p>{{ month }} · {{ mode === 'demo' ? '本地演示' : '根据该月消费统计生成' }}</p>
                </div>
                <button class="secondary" type="button" :disabled="loading || adviceLoading || noAdviceBills" @click="generateAdvice">
                  {{ adviceLoading ? '正在生成…' : '生成消费建议' }}
                </button>
              </div>
              <div v-if="noAdviceBills || adviceStatus || advice" class="overview-advice-result" role="status">
                <template v-if="noAdviceBills">{{ emptyAdviceMessage }}</template>
                <template v-else><p v-if="adviceStatus">{{ adviceStatus }}</p><p v-if="adviceStale && advice">账单已变动，以下是上次生成的建议。</p><p v-if="advice">{{ advice }}</p></template>
              </div>
            </section>
            <div class="section-bar"><h2>{{page==='账单明细'?'账单记录':'收支概览'}} <span>{{mode==='demo'?'DEMO DATA':'MY LEDGER'}}</span></h2><input class="month" type="month" :value="month" @change="month = $event.target.value || month; $event.target.value = month" aria-label="选择月份"></div>
            <div class="metrics"><section class="metric dark"><div><span>本月总支出</span><Wallet :size="19"/></div><h3><small>¥</small>{{money(total)}}</h3><p><span class="metric-chip">{{monthly.length}} 笔记录</span> 每一笔，都值得被看见</p><div class="metric-orbit"></div></section><section class="metric"><div><span>日均消费</span><ArrowUpRight :size="19"/></div><h3><small>¥</small>{{money(total/(month===currentMonth?new Date().getDate():new Date(Number(month.slice(0,4)),Number(month.slice(5)),0).getDate()))}}</h3><p>按所选月份已过天数计算</p></section><section class="metric" :class="{ 'over-budget': totalBudget?.status === 'OVER' }">
                <div><span>{{totalBudget?.status === 'OVER' ? '总预算已超支' : '总预算剩余'}}</span><Leaf :size="19"/></div>
                <template v-if="totalBudget">
                  <h3><small>¥</small>{{money(totalBudget.status === 'OVER' ? totalBudget.excess : totalBudget.remaining)}}</h3>
                  <div class="progress"><i :style="{width:Math.min(totalBudget.percent,100)+'%', background:totalBudget.status==='OVER'?'#c4735e':''}"></i></div>
                  <p>已用 {{totalBudget.percent}}% <span v-if="totalBudget.status === 'AT_LIMIT'">· 已用完</span></p>
                </template>
                <p v-else>{{ budgetError ? '预算查询失败' : budgetSnapshot ? '未设置总预算' : '正在加载预算…' }}</p>
                <button class="text-button" type="button" :disabled="!month" @click="editorMonth=month">调整</button>
              </section></div>
            <p v-if="budgetError" class="budget-error" role="alert">预算查询失败：{{budgetError}}
              <button type="button" class="text-button" @click="loadBudget">重试预算</button></p>
            <div v-if="['NEAR','AT_LIMIT'].includes(totalBudget?.status)" class="budget-notice" role="status">
              {{month}} 总预算{{budgetStatus[totalBudget.status]}} · 已用 {{totalBudget.percent}}%
            </div>
            <div v-if="alerts.length" class="budget-alert" role="alert">
              <span class="budget-alert-symbol">!</span>
              <div v-for="item in alerts" :key="item.category || 'total'"><strong>{{item.category || '总预算'}}超支 ¥{{money(item.excess)}}</strong></div>
            </div>
            <BudgetForm v-if="editorMonth" :key="mode + editorMonth" :mode="mode" :month="editorMonth"
              :categories="categories" :demo-bills="demoBills" closeable @close="editorMonth=''" @saved="budgetSaved"/>
            <div v-if="page!=='账单明细'" class="charts-grid"><section class="card trend-card"><div class="card-heading"><div><h2>{{page==='总览'?'近半年消费趋势':page==='消费分析'&&trend?'月度支出趋势':'每日消费趋势'}}</h2><p v-if="page === '总览'">{{ overviewMonths[0] }} 至 {{ month }} · 按月汇总</p><p v-else>给消费一点觉察，让生活多一份从容</p></div><span class="legend"><i></i>支出金额</span></div><template v-if="page === '总览'">
              <div v-if="loading" class="empty" role="status">正在加载半年趋势…</div>
              <div v-else-if="!overviewTrend" class="empty">暂时无法加载半年趋势。<button class="text-button" type="button" @click="load">重试</button></div>
              <template v-else>
                <Chart :option="lineOption" :aria-label="overviewTrendLabel"/>
                <div v-if="overviewTrend.totals.every(value => value === 0)" class="empty">这六个月还没有消费记录</div>
              </template>
            </template><template v-else><Chart :option="lineOption"/><div v-if="!monthly.length&&!trend" class="empty">本月还没有消费记录</div></template></section><section class="card category-card"><div class="card-heading"><h2>钱都花在哪儿</h2><SlidersHorizontal :size="17"/></div><div class="pie-wrap"><Chart :option="pieOption"/><div class="pie-label"><small>支出分布</small><strong>{{groups.length}}<small> 类</small></strong></div></div><div class="category-legend"><div v-for="g in (page==='消费分析' ? groups : groups.slice(0,4))" :key="g.category"><i :style="{background:colors[categories.indexOf(g.category)]}"></i><span>{{g.category}}
              <small v-if="page==='消费分析' && budgetStatus[categoryBudgets[g.category]?.status]"
                class="budget-tag" :class="categoryBudgets[g.category].status">{{budgetStatus[categoryBudgets[g.category].status]}}</small>
              </span><b>{{money(g.total)}}</b></div></div><p v-if="!groups.length" class="empty">记下第一笔，开始了解自己</p></section></div>
            <section class="card bills-card"><div class="card-heading"><div><h2>{{page==='账单明细'?'全部账单':'最近账单'}} <span class="count">{{filtered.length}}</span></h2><p>认真记录，生活中的每一个小日常</p></div><button class="secondary" @click="exportCSV"><Download :size="15"/>导出账单</button></div><div class="filters"><label class="search"><Search :size="17"/><input v-model="keyword" placeholder="搜索商户或备注…" aria-label="搜索账单"></label><select v-model="category" aria-label="筛选分类"><option value="">全部分类</option><option v-for="c in categories">{{c}}</option></select><button v-if="keyword||category" class="text-button" @click="keyword='';category=''">清除筛选</button><span class="filter-total">筛选合计 ¥{{money(filtered.reduce((s,b)=>s+Number(b.amount),0))}}</span></div><div class="table-scroll"><table><thead><tr><th>消费项目</th><th>分类</th><th>消费时间</th><th>金额</th><th></th></tr></thead><tbody><tr v-for="bill in (page==='账单明细'?filtered:filtered.slice(0,5))" :key="bill.id"><td><div class="merchant"><span class="category-icon" :style="{background:colors[categories.indexOf(bill.category)]+'22'}">{{icons[categories.indexOf(bill.category)]||'🧾'}}</span><div><b>{{bill.merchant||bill.category}}</b><small>{{bill.note||'日常消费'}}</small></div></div></td><td><span class="tag">{{bill.category}}</span></td><td class="date">{{displayBillTime(bill)}}</td><td class="amount">− ¥{{money(bill.amount)}}</td><td><button class="delete icon" aria-label="删除账单" @click="pendingDelete=bill.id"><Trash2 :size="15"/></button></td></tr></tbody></table><div v-if="loading" class="empty">正在加载账本…</div><div v-else-if="!filtered.length" class="empty"><ReceiptText :size="28"/><p>{{keyword||category?'没有找到匹配账单':'还没有账单，记录今天的第一笔吧。'}}</p><button class="text-button" @click="openManual()">添加一笔消费</button></div></div><button v-if="page!=='账单明细'&&filtered.length>5" class="table-footer" @click="page='账单明细'">查看全部账单 <ChevronRight :size="14"/></button></section>
          </template>
        </template>
        <footer><span><Leaf :size="14"/> 钱花得明白，生活过得自在。</span><span>小账 POCKET AI · 记录生活的分寸</span></footer>
      </div>
    </main>
    <ManualBill v-if="manual" :mode="manual.mode" :month="manual.month" :categories="categories" :save-demo="saveDemo" @close="manual=null" @recorded="manualRecorded"/>
    <div v-if="pendingDelete!==null" class="overlay"><section class="dialog compact" role="alertdialog" aria-modal="true"><h2>删除这笔账单？</h2><p>删除后无法恢复，请确认这笔记录不再需要。</p><div class="dialog-actions"><button class="secondary" :disabled="busy" @click="pendingDelete=null">取消</button><button class="danger" :disabled="busy" @click="remove">{{busy?'删除中…':'确认删除'}}</button></div></section></div>
    <div v-if="advicePrompt" class="overlay">
      <section class="dialog compact" role="alertdialog" aria-modal="true" aria-labelledby="advice-prompt-title" @keydown.esc="advicePrompt = ''">
        <h2 id="advice-prompt-title">{{ advicePrompt }}</h2>
        <div class="dialog-actions">
          <button class="secondary" type="button" @click="advicePrompt = ''">暂不生成</button>
          <button class="primary" type="button" :disabled="loading" @click="page = '总览'; generateAdvice()">重新生成</button>
        </div>
      </section>
    </div>
    <div v-if="toast" class="toast" role="status">{{toast}}</div>
  </div>
</template>
