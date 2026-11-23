import { api } from './api'

const defaultsKey = 'pocket-demo-budget-defaults'
const monthKey = month => `pocket-demo-budget-${month}`
const read = key => JSON.parse(localStorage.getItem(key))

export const budgetStore = {
  defaults(mode) {
    return mode === 'live' ? api.budgetDefaults() : { budgets: read(defaultsKey) || [] }
  },
  setDefaults(mode, budgets) {
    if (mode === 'live') return api.setBudgetDefaults(budgets)
    localStorage.setItem(defaultsKey, JSON.stringify(budgets))
    return { budgets }
  },
  month(mode, month, bills) {
    if (mode === 'live') return api.budgetMonth(month)
    const custom = read(monthKey(month))
    const budgets = (custom || read(defaultsKey) || []).map(({ category, amount }) => {
      const limit = Math.round(amount * 100)
      const spent = bills.filter(bill => bill.occurredAt?.startsWith(month) && (!category || bill.category === category))
        .reduce((total, bill) => total + Math.round(bill.amount * 100), 0)
      const status = spent > limit ? 'OVER' : spent === limit ? 'AT_LIMIT' : spent * 5 >= limit * 4 ? 'NEAR' : 'NORMAL'
      return { category, amount, status, spent: spent / 100, remaining: Math.max(0, limit - spent) / 100,
        excess: Math.max(0, spent - limit) / 100, percent: spent / limit * 100 }
    })
    return { month, source: custom ? 'CUSTOM' : 'DEFAULT', budgets }
  },
  setMonth(mode, month, budgets) {
    if (mode === 'live') return api.setBudgetMonth(month, budgets)
    localStorage.setItem(monthKey(month), JSON.stringify(budgets))
  },
  restore(mode, month) {
    if (mode === 'live') return api.restoreBudgetMonth(month)
    localStorage.removeItem(monthKey(month))
  },
}
