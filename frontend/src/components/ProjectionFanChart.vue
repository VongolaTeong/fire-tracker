<script setup>
import { computed } from 'vue';
import { Line } from 'vue-chartjs';
import { projectionToFanData } from '../charts/mappers.js';
import { money } from '../format.js';

const props = defineProps({
  projection: { type: Object, default: null },
});

const chartData = computed(() => projectionToFanData(props.projection));
const hasBands = computed(() => (props.projection?.bands?.length ?? 0) > 0);

const options = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { intersect: false, mode: 'index' },
  plugins: {
    legend: { position: 'bottom' },
    tooltip: { callbacks: { label: (ctx) => `${ctx.dataset.label}: ${money(ctx.parsed.y)}` } },
  },
  scales: {
    y: { ticks: { callback: (v) => money(v) } },
  },
};
</script>

<template>
  <div v-if="hasBands" class="chart-wrap">
    <Line :data="chartData" :options="options" />
  </div>
  <p v-else class="banner banner--muted">No projection available.</p>
</template>
