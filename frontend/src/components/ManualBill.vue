<script setup>
import { ref, onMounted } from 'vue'
import { X, Check } from 'lucide-vue-next'
import { api } from '../api'
const props = defineProps({ mode: String, month: String, categories: Array, saveDemo: Function })
const emit = defineEmits(['close', 'recorded'])
const now = new Date()
const localDay = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
const draft = ref({ amount:'', category:'', merchant:'', note:'', occurredAt:(localDay.startsWith(props.month)?localDay:props.month+'-01')+'T12:00' })
const saving = ref(false), error = ref(''), amountInput = ref(null)
let lastPayload = '', actionId = crypto.randomUUID()
onMounted(() => amountInput.value?.focus())
function close() { if(!saving.value) emit('close') }
async function save() {
  if(saving.value) return
  const d = draft.value, amount = Number(d.amount)
  if(!Number.isFinite(amount) || amount<=0 || amount>9999999999.99 || Math.abs(amount*100-Math.round(amount*100))>0.00001 || !props.categories.includes(d.category) || !d.occurredAt) {
    error.value='请填写有效金额（最多两位小数）、分类和消费时间'; return
  }
  const bill={ amount,category:d.category,merchant:d.merchant.trim()||null,note:d.note.trim()||null,occurredAt:d.occurredAt.replace('T',' ')+':00',timePrecision:'EXACT' }
  // 网络失败时原样重试沿用动作ID，防止重复入账；修改表单后创建新动作。
  const payload=JSON.stringify(bill)
  if(lastPayload && payload!==lastPayload) actionId=crypto.randomUUID()
  lastPayload=payload; saving.value=true; error.value=''
  try {
    if(props.mode==='demo') await props.saveDemo(actionId,bill)
    else await api.chatRecord({actionId,bill})
    emit('close')
    emit('recorded', props.mode, { type: 'added', month: bill.occurredAt.slice(0, 7) })
  } catch(e) { error.value=e.message||'保存失败，请重试' }
  finally { saving.value=false }
}
</script>
<template>
  <div class="overlay" @click.self="close" @keydown.esc="close">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="manual-title">
      <div class="dialog-heading"><h2 id="manual-title">手动记一笔</h2><button class="icon" :disabled="saving" aria-label="关闭手动记账" @click="close"><X/></button></div>
      <p>填好后确认入账。{{mode==='demo'?'本次保存到演示账本。':'本次保存到后端账本。'}}</p>
      <form @submit.prevent="save">
        <fieldset :disabled="saving" class="manual-fields">
          <label>消费金额（元）<input ref="amountInput" v-model="draft.amount" type="number" min="0.01" max="9999999999.99" step="0.01" required placeholder="0.00" class="amount-input"></label>
          <div class="form-grid"><label>分类<select v-model="draft.category" required><option value="" disabled>请选择分类</option><option v-for="c in categories" :key="c">{{c}}</option></select></label><label>消费时间<input v-model="draft.occurredAt" type="datetime-local" required></label></div>
          <label>商户<input v-model="draft.merchant" maxlength="64" placeholder="例如：学校食堂"></label>
          <label>备注<textarea v-model="draft.note" rows="2" maxlength="255" placeholder="记录这笔消费"></textarea></label>
        </fieldset>
        <p v-if="error" class="budget-error" role="alert">{{error}}</p>
        <div class="dialog-actions"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" class="primary" :disabled="saving"><Check :size="17"/>{{saving?'保存中…':'确认入账'}}</button></div>
      </form>
    </section>
  </div>
</template>
