<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
const props = defineProps({ option: Object })
const element = ref(null)
let chart, observer
onMounted(() => { chart = echarts.init(element.value); chart.setOption(props.option); observer = new ResizeObserver(() => chart.resize()); observer.observe(element.value) })
watch(() => props.option, value => chart?.setOption(value, true), { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="element" class="chart" role="img" aria-label="消费统计图表"></div></template>
