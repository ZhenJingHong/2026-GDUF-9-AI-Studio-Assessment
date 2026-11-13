<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Wallet, LayoutDashboard, ArrowUpRight, ArrowDownRight, Plus, Search, Sparkles, SlidersHorizontal, ReceiptText, ChartNoAxesCombined, Settings2, ChevronRight, X, Send, Upload, Trash2, Download, Leaf, CircleHelp, Check, Menu } from 'lucide-vue-next'
import Chart from './components/Chart.vue'
import { api } from './api'
const categories = ['餐饮','交通','购物','娱乐','居住','医疗','教育','其他']
const colors = ['#34765b','#b6ce8b','#e8bb7b','#899cce','#d69385','#86bbbc','#c4b7d7','#c4c8c4']
const icons = ['🍜','🚕','🛍️','🎮','🏠','💊','📚','🧩']
function localDate(date = new Date()) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}` }
const currentMonth = localDate().slice(0,7)
const month = ref(currentMonth), page = ref('总览'), mode = ref(localStorage.getItem('pocket-mode') || 'demo'), mobile = ref(false)
const bills = ref([]), stats = ref(null), trend = ref(null), loading = ref(false), busy = ref(false), error = ref(''), toast = ref('')
const keyword = ref(''), category = ref(''), input = ref(''), intent = ref('记一笔'), modal = ref(false), draft = ref({}), answer = ref(''), pendingDelete = ref(null), budget = ref(Number(localStorage.getItem('pocket-budget')) || 5000)
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function seed() { return Array.from({length:36}, (_, i) => ({ id: i+1, amount: [28,18,128,36,25,68,52,19.9,239,42,12,88][i%12], category: categories[i%8], merchant: ['校园食堂','滴滴出行','生活好物','周末电影','日常生活','健康药房','书店','便利店'][i%8], note: ['午餐，认真吃饭','出门办事','给生活添一点新意','休息一下','日常开销','健康第一','学习投资','随手记录'][i%8], occurredAt: `${currentMonth}-${String(Math.max(1, new Date().getDate() - Math.floor(i/3))).padStart(2,'0')} ${String(10+i%10).padStart(2,'0')}:30:00` })) }
function notify(text) { toast.value = text; setTimeout(() => { if(toast.value === text) toast.value = '' }, 3500) }
async function load() {
  loading.value = true; error.value = ''; stats.value = null; trend.value = null
  try {
    if (mode.value === 'demo') { const saved = localStorage.getItem('pocket-bills'); bills.value = saved ? JSON.parse(saved) : seed() }
    else {
      bills.value = await api.bills({month:month.value})
      const from = new Date(`${month.value}-01T12:00:00`); from.setMonth(from.getMonth()-5)
      const results = await Promise.allSettled([api.category(month.value),api.trend(localDate(from).slice(0,7),month.value)])
      if(results[0].status === 'fulfilled') stats.value = results[0].value
      if(results[1].status === 'fulfilled') trend.value = results[1].value
      if(results.some(r=>r.status==='rejected')) notify('部分统计接口尚未就绪，已根据账单计算图表')
    }
  } catch(e) { bills.value = []; error.value = e.message || '连接失败' } finally { loading.value = false }
}
watch([month,mode], () => { localStorage.setItem('pocket-mode',mode.value); load() })
watch(budget, value => localStorage.setItem('pocket-budget',String(value)))
onMounted(load)
const monthly = computed(() => bills.value.filter(b => b.occurredAt?.startsWith(month.value)))
const filtered = computed(() => monthly.value.filter(b => (!category.value || b.category===category.value) && `${b.merchant||''}${b.note||''}`.toLowerCase().includes(keyword.value.toLowerCase())).sort((a,b)=>b.occurredAt.localeCompare(a.occurredAt)))
const total = computed(() => monthly.value.reduce((s,b)=>s+Math.round(Number(b.amount)*100),0)/100)
const groups = computed(() => stats.value?.items || categories.map(c=>({ category:c,total:monthly.value.filter(b=>b.category===c).reduce((s,b)=>s+Number(b.amount),0),count:monthly.value.filter(b=>b.category===c).length })).filter(x=>x.count).sort((a,b)=>b.total-a.total))
const spent = computed(() => budget.value>0 ? total.value/budget.value*100 : 0)
const lineOption = computed(() => {
  let labels, values
  if(page.value==='消费分析' && trend.value) { labels=trend.value.months; values=trend.value.totals }
  else { const days = new Date(Number(month.value.slice(0,4)),Number(month.value.slice(5)),0).getDate(); labels=Array.from({length:days},(_,i)=>String(i+1)); values=labels.map(d=>monthly.value.filter(b=>Number(b.occurredAt.slice(8,10))===Number(d)).reduce((s,b)=>s+Number(b.amount),0)) }
  return { tooltip:{trigger:'axis',valueFormatter:v=>`¥${money(v)}`},grid:{left:45,right:18,top:24,bottom:25},xAxis:{type:'category',data:labels,axisLine:{show:false},axisTick:{show:false},axisLabel:{color:'#8b968f',interval:Math.max(0,Math.floor(labels.length/7)-1)}},yAxis:{type:'value',splitLine:{lineStyle:{color:'#edf0eb',type:'dashed'}},axisLabel:{color:'#8b968f'}},series:[{type:'line',smooth:true,data:values,symbol:'none',lineStyle:{color:'#34765b',width:3},areaStyle:{color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'#b7d7beaa'},{offset:1,color:'#ffffff00'}]}}}] }
})
const pieOption = computed(() => ({tooltip:{trigger:'item',formatter:'{b}: ¥{c} ({d}%)'},color:groups.value.map(x=>colors[categories.indexOf(x.category)]),series:[{type:'pie',radius:['65%','85%'],center:['50%','50%'],label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:5,borderRadius:6},data:groups.value.map(x=>({name:x.category,value:Number(x.total.toFixed(2))}))}]}))
function openDraft(data={}) { draft.value={amount:'',category:'',merchant:'',note:'',occurredAt:`${localDate()}T12:00`,...data}; if(data.occurredAt) draft.value.occurredAt=data.occurredAt.replace(' ','T').slice(0,16); modal.value=true }
function persist() { localStorage.setItem('pocket-bills',JSON.stringify(bills.value)) }
async function save() {
  if(busy.value) return
  const d=draft.value
  if(!(Number(d.amount)>0) || !categories.includes(d.category) || !d.occurredAt) return notify('请填写有效金额、分类和消费时间')
  busy.value=true
  try { const payload={amount:Number(Number(d.amount).toFixed(2)),category:d.category,merchant:d.merchant||null,note:d.note||'',occurredAt:d.occurredAt.replace('T',' ')+':00'}; if(mode.value==='demo') { bills.value.push({...payload,id:crypto.randomUUID()});persist() } else { await api.add(payload); await load() } modal.value=false; notify('这笔消费，记好啦') } catch(e){notify(e.message)} finally{busy.value=false}
}
async function remove() { if(busy.value) return; busy.value=true; try{ if(mode.value==='live') await api.remove(pendingDelete.value); bills.value=bills.value.filter(b=>b.id!==pendingDelete.value); if(mode.value==='demo')persist(); pendingDelete.value=null; if(mode.value==='live')await load();notify('账单已删除') }catch(e){notify(e.message)}finally{busy.value=false} }
async function submit() {
  if(!input.value.trim() || busy.value) return
  busy.value=true; answer.value=''
  try {
    if(mode.value==='demo') {
      if(intent.value==='查消费') { answer.value=`当前所选 ${month.value} 的演示账本共 ${monthly.value.length} 笔，支出 ¥${money(total.value)}。演示模式不调用 AI，切换后端模式即可使用自然语言查账。`;return }
      const amount=input.value.match(/(\d+(?:\.\d{1,2})?)\s*(?:元|块|¥)?/)
      const cat=/打车|地铁|公交/.test(input.value)?'交通':/饭|餐|咖啡|奶茶/.test(input.value)?'餐饮':''
      openDraft({amount:amount?Number(amount[1]):'',category:cat,note:input.value});notify('演示提取结果，请核对后保存');return
    }
    if(intent.value==='记一笔') { openDraft(await api.parse(input.value));return }
    const spec=await api.query(input.value)
    if(!['stat','list'].includes(spec.action)) {answer.value='没听懂，能换个说法吗？';return}
    if(!/^\d{4}-\d{2}-\d{2}$/.test(spec.start||'') || !/^\d{4}-\d{2}-\d{2}$/.test(spec.end||'') || spec.start>spec.end) throw new Error('AI 返回的查询日期无效，请换个说法')
    if(spec.action==='stat') {
      const aggregate=spec.aggregate||'sum'
      if(!['sum','count','max','avg'].includes(aggregate)) throw new Error('暂不支持该统计方式')
      const result=await api.range({start:spec.start,end:spec.end,category:spec.category,keyword:spec.keyword,aggregate})
      const value=result.value==null?'暂无数据':aggregate==='count'?`${result.value} 笔`:`¥${money(result.value)}`
      answer.value=`${spec.start} 至 ${spec.end} · ${spec.category||'全部分类'}\n${{sum:'支出合计',count:'账单数量',max:'最大单笔',avg:'平均消费'}[aggregate]}：${value}。\n结果来自后端按日期范围统计。`
      return
    }
    const rows=await api.bills({category:spec.category,keyword:spec.keyword})
    const selected=rows.filter(b=>b.occurredAt.slice(0,10)>=spec.start && b.occurredAt.slice(0,10)<=spec.end)
    if(spec.action==='list') {answer.value=`${spec.start} 至 ${spec.end}，找到 ${selected.length} 笔：\n`+selected.map(b=>`${b.occurredAt.slice(0,10)} · ${b.merchant||b.category} · ¥${money(b.amount)}`).join('\n');return}
  }catch(e){notify(e.message)}finally{busy.value=false}
}
async function upload(event) { const file=event.target.files?.[0];event.target.value='';if(!file)return;if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>10*1024*1024)return notify('请选择 10MB 以内的 JPG、PNG 或 WebP 图片');if(mode.value==='demo')return notify('小票识别需要连接后端，请先切换模式');busy.value=true;try{openDraft(await api.ocr(file))}catch(e){notify(e.message)}finally{busy.value=false} }
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
        <div class="title-row"><div><div class="eyebrow">YOUR MONEY, A LITTLE CLEARER</div><h1>{{page==='总览'?'生活有记录，花钱有主张。':page==='AI 助手'?'一句话，把账记明白。':page==='账单明细'?'每一笔，都有迹可循。':page==='消费分析'?'看见消费，找到生活的节奏。':'你的账本，你来定义。'}}</h1><p>{{page==='总览'?'欢迎回来，一起看看最近的钱都花在哪儿了。':'让记账成为一件轻松的小事。'}}</p></div><button class="primary" @click="openDraft()"><Plus :size="18"/>记一笔</button></div>
        <div v-if="error" class="error" role="alert">{{error}}。请确认后端已启动，或在设置中切换本地演示。<button @click="load">重试</button></div>
        <template v-if="page==='设置'">
          <section class="card settings-card"><h2>账本设置</h2><p>演示与真实账本独立保存，切换模式不会迁移数据。</p><label>数据来源<select v-model="mode"><option value="demo">本地演示 · 无需后端</option><option value="live">连接 Spring Boot 后端</option></select></label><label>每月预算（元）<input v-model.number="budget" type="number" min="1" max="100000000" @change="budget=Math.max(1,Number(budget)||5000)"></label><div class="notice">预算仅保存在当前浏览器；后端预算接口尚未提供。演示模式中的自然语言提取是本地规则，并非大模型识别。</div></section>
        </template>
        <template v-else>
          <section v-if="['总览','AI 助手'].includes(page)" class="ai-card"><div class="ai-heading"><span class="sparkle"><Sparkles :size="22"/></span><div><h2>记账这件事，交给小账 AI <span>BETA</span></h2><p>一句话记消费，一张图识小票。少一点操作，多一点生活。</p></div><span class="ai-decoration">✳</span></div><form @submit.prevent="submit"><div class="ai-input"><input v-model="input" :placeholder="intent==='记一笔'?'试试说：今天午饭花了 28 元，在学校食堂':'试试问：上个月餐饮一共花了多少？'" aria-label="AI 输入"><button class="send" :disabled="busy||!input.trim()" aria-label="发送"><Send :size="19"/></button></div></form><div class="ai-footer"><div class="tabs"><button v-for="i in ['记一笔','查消费']" :class="{selected:intent===i}" @click="intent=i">{{i==='记一笔'?'✦':'⌕'}} {{i}}</button><label class="upload"><Upload :size="14"/>识别小票<input type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy" @change="upload"></label></div><span>{{busy?'正在处理，请稍候…':'识别后由你确认，再保存入账'}}</span></div><p v-if="answer" class="answer" aria-live="polite">{{answer}}</p><div v-if="page==='AI 助手'" class="suggestions"><button v-for="s in ['今天坐地铁花了6元','这个月购物花了多少','昨天买咖啡花了22元']" @click="input=s;intent=s.includes('多少')?'查消费':'记一笔'">{{s}} <ArrowUpRight :size="15"/></button></div></section>
          <template v-if="page!=='AI 助手'">
            <div class="section-bar"><h2>{{page==='账单明细'?'账单记录':'收支概览'}} <span>{{mode==='demo'?'DEMO DATA':'MY LEDGER'}}</span></h2><input class="month" type="month" v-model="month" aria-label="选择月份"></div>
            <div class="metrics"><section class="metric dark"><div><span>本月总支出</span><Wallet :size="19"/></div><h3><small>¥</small>{{money(total)}}</h3><p><span class="metric-chip">{{monthly.length}} 笔记录</span> 每一笔，都值得被看见</p><div class="metric-orbit"></div></section><section class="metric"><div><span>日均消费</span><ArrowUpRight :size="19"/></div><h3><small>¥</small>{{money(total/(month===currentMonth?new Date().getDate():new Date(Number(month.slice(0,4)),Number(month.slice(5)),0).getDate()))}}</h3><p>按所选月份已过天数计算</p></section><section class="metric"><div><span>本月预算剩余</span><Leaf :size="19"/></div><h3><small>¥</small>{{money(budget-total)}}</h3><div class="progress"><i :style="{width:Math.min(spent,100)+'%',background:spent>100?'#c4735e':''}"></i></div><p>{{spent>100?'已超出预算':'已使用预算'}} {{spent.toFixed(1)}}% <button class="text-button" @click="page='设置'">调整</button></p></section></div>
            <div v-if="page!=='账单明细'" class="charts-grid"><section class="card trend-card"><div class="card-heading"><div><h2>{{page==='消费分析'&&trend?'月度支出趋势':'每日消费趋势'}}</h2><p>给消费一点觉察，让生活多一份从容</p></div><span class="legend"><i></i>支出金额</span></div><Chart :option="lineOption"/><div v-if="!monthly.length&&!trend" class="empty">本月还没有消费记录</div></section><section class="card category-card"><div class="card-heading"><h2>钱都花在哪儿</h2><SlidersHorizontal :size="17"/></div><div class="pie-wrap"><Chart :option="pieOption"/><div class="pie-label"><small>支出分布</small><strong>{{groups.length}}<small> 类</small></strong></div></div><div class="category-legend"><div v-for="g in groups.slice(0,4)"><i :style="{background:colors[categories.indexOf(g.category)]}"></i><span>{{g.category}}</span><b>{{money(g.total)}}</b></div></div><p v-if="!groups.length" class="empty">记下第一笔，开始了解自己</p></section></div>
            <section class="card bills-card"><div class="card-heading"><div><h2>{{page==='账单明细'?'全部账单':'最近账单'}} <span class="count">{{filtered.length}}</span></h2><p>认真记录，生活中的每一个小日常</p></div><button class="secondary" @click="exportCSV"><Download :size="15"/>导出账单</button></div><div class="filters"><label class="search"><Search :size="17"/><input v-model="keyword" placeholder="搜索商户或备注…" aria-label="搜索账单"></label><select v-model="category" aria-label="筛选分类"><option value="">全部分类</option><option v-for="c in categories">{{c}}</option></select><button v-if="keyword||category" class="text-button" @click="keyword='';category=''">清除筛选</button><span class="filter-total">筛选合计 ¥{{money(filtered.reduce((s,b)=>s+Number(b.amount),0))}}</span></div><div class="table-scroll"><table><thead><tr><th>消费项目</th><th>分类</th><th>消费时间</th><th>金额</th><th></th></tr></thead><tbody><tr v-for="bill in (page==='账单明细'?filtered:filtered.slice(0,5))" :key="bill.id"><td><div class="merchant"><span class="category-icon" :style="{background:colors[categories.indexOf(bill.category)]+'22'}">{{icons[categories.indexOf(bill.category)]||'🧾'}}</span><div><b>{{bill.merchant||bill.category}}</b><small>{{bill.note||'日常消费'}}</small></div></div></td><td><span class="tag">{{bill.category}}</span></td><td class="date">{{bill.occurredAt?.slice(0,16)}}</td><td class="amount">− ¥{{money(bill.amount)}}</td><td><button class="delete icon" aria-label="删除账单" @click="pendingDelete=bill.id"><Trash2 :size="15"/></button></td></tr></tbody></table><div v-if="loading" class="empty">正在加载账本…</div><div v-else-if="!filtered.length" class="empty"><ReceiptText :size="28"/><p>{{keyword||category?'没有找到匹配账单':'还没有账单，记录今天的第一笔吧。'}}</p><button class="text-button" @click="openDraft()">添加一笔消费</button></div></div><button v-if="page!=='账单明细'&&filtered.length>5" class="table-footer" @click="page='账单明细'">查看全部账单 <ChevronRight :size="14"/></button></section>
          </template>
        </template>
        <footer><span><Leaf :size="14"/> 钱花得明白，生活过得自在。</span><span>小账 POCKET AI · 记录生活的分寸</span></footer>
      </div>
    </main>
    <div v-if="modal" class="overlay" @click.self="!busy&&(modal=false)"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="draft-title"><div class="dialog-heading"><div><span class="eyebrow">A LITTLE MOMENT OF LIFE</span><h2 id="draft-title">确认这笔消费</h2></div><button class="icon" :disabled="busy" aria-label="关闭" @click="modal=false"><X/></button></div><p>核对金额、分类和时间，确认后才会保存。</p><form @submit.prevent="save"><label>消费金额（元）<input v-model="draft.amount" required type="number" min="0.01" max="99999999" step="0.01" placeholder="0.00" class="amount-input"></label><div class="form-grid"><label>分类<select v-model="draft.category" required><option value="" disabled>请选择分类</option><option v-for="c in categories">{{c}}</option></select></label><label>消费时间<input v-model="draft.occurredAt" type="datetime-local" required></label></div><label>商户<input v-model="draft.merchant" maxlength="100" placeholder="例如：学校食堂"></label><label>备注<textarea v-model="draft.note" maxlength="500" rows="2" placeholder="给这笔消费留个小注脚"></textarea></label><div class="dialog-actions"><button type="button" class="secondary" :disabled="busy" @click="modal=false">再想想</button><button class="primary" :disabled="busy"><Check :size="17"/>{{busy?'保存中…':'确认入账'}}</button></div></form></section></div>
    <div v-if="pendingDelete!==null" class="overlay"><section class="dialog compact" role="alertdialog" aria-modal="true"><h2>删除这笔账单？</h2><p>删除后无法恢复，请确认这笔记录不再需要。</p><div class="dialog-actions"><button class="secondary" :disabled="busy" @click="pendingDelete=null">取消</button><button class="danger" :disabled="busy" @click="remove">{{busy?'删除中…':'确认删除'}}</button></div></section></div>
    <div v-if="toast" class="toast" role="status">{{toast}}</div>
  </div>
</template>
