<script setup>
import { ref, onMounted, watch } from 'vue'
import { budgetStore } from '../budgets'

const props = defineProps({ mode: String, month: String, categories: Array, demoBills: Function, closeable: Boolean, defaultsVersion: Number })
const emit = defineEmits(['saved', 'close'])
const entries = ref({})
const error = ref('')
const loading = ref(true)
const busy = ref(false)
const source = ref('')
const loaded = ref(false)
const edited = new Set()
let cleared = false
const legacy = Number(localStorage.getItem('pocket-budget'))
const scopes = [null, ...props.categories]
const key = category => category || 'total'
const demoRows = () => props.mode === 'demo' ? props.demoBills() : []

function populate(snapshot) {
  entries.value = Object.fromEntries(snapshot.budgets.map(item => [key(item.category), String(item.amount)]))
  source.value = snapshot.source
  loaded.value = true
  edited.clear()
  cleared = false
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    populate(await (props.month ? budgetStore.month(props.mode, props.month, demoRows())
      : budgetStore.defaults(props.mode)))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => props.defaultsVersion, async () => {
  if (source.value !== 'DEFAULT' || busy.value) return
  try {
    const latest = await budgetStore.month(props.mode, props.month, demoRows())
    if (source.value !== 'DEFAULT' || busy.value || cleared) return
    for (const category of scopes) {
      const name = key(category)
      if (!edited.has(name)) {
        const item = latest.budgets.find(budget => budget.category === category)
        entries.value[name] = item ? String(item.amount) : ''
      }
    }
  } catch (e) {
    error.value = e.message
  }
})

function clear() {
  entries.value = {}
  cleared = true
}

function useLegacy() {
  entries.value.total = String(legacy)
  edited.add('total')
}

async function save(restore = false) {
  if (busy.value) return
  error.value = ''
  const budgets = []
  if (!restore) {
    for (const category of scopes) {
      const raw = (entries.value[key(category)] || '').trim()
      if (!raw) continue
      const amount = Number(raw)
      if (!/^\d+(?:\.\d{1,2}0*)?$/.test(raw) || amount < 0.01 || amount > 100000000) {
        error.value = `${category || '总预算'}请输入 0.01 至 100000000 元，最多两位有效小数。没有预算请留空。`
        return
      }
      budgets.push({ category, amount })
    }
  }
  busy.value = true
  try {
    const result = restore ? await budgetStore.restore(props.mode, props.month)
      : props.month ? await budgetStore.setMonth(props.mode, props.month, budgets)
        : await budgetStore.setDefaults(props.mode, budgets)
    emit('saved', restore ? '已恢复跟随默认' : '预算已保存')
    if (result) populate(result)
    else await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card budget-editor">
    <div class="card-heading">
      <div><h2>{{ month ? month + ' 月度预算' : '全局默认预算' }}</h2>
        <p v-if="month && source">{{ source === 'CUSTOM' ? '该月使用独立预算' : '该月跟随全局默认，保存后将独立设置' }}</p>
        <p v-else-if="!month">适用于跟随默认的月份</p></div>
      <button v-if="closeable" type="button" class="text-button" @click="emit('close')">关闭</button>
    </div>
    <p v-if="loading" role="status">正在加载预算…</p>
    <p v-if="error" class="budget-error" role="alert">{{ error }}</p>
    <button v-if="!loading && error && !loaded" class="secondary" @click="load">重新加载预算</button>
    <form v-if="!loading && loaded" @submit.prevent="save()">
      <p>留空表示该项没有预算。金额单位为元。</p>
      <p v-if="!month && legacy > 0 && legacy <= 100000000 && !entries.total">
        此浏览器的旧总预算为 ¥{{ legacy }}。填写或沿用后，点击保存才会生效。
        <button class="text-button" type="button" @click="useLegacy">沿用旧额度</button>
      </p>
      <div class="budget-inputs">
        <label v-for="category in scopes" :key="key(category)">{{ category || '总预算' }}
          <input v-model="entries[key(category)]" @input="edited.add(key(category))" inputmode="decimal" placeholder="无预算" :disabled="busy">
        </label>
      </div>
      <div class="budget-buttons">
        <button class="primary" :disabled="busy">保存预算</button>
        <button class="secondary" type="button" :disabled="busy" @click="clear">全部清空</button>
        <button v-if="month && source === 'CUSTOM'" class="secondary" type="button" :disabled="busy"
          @click="save(true)">恢复跟随默认</button>
      </div>
    </form>
  </section>
</template>
