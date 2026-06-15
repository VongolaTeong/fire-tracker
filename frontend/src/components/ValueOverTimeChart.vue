<script setup>
import { computed } from 'vue';
import { Line } from 'vue-chartjs';
import { valueHistoryToChartData } from '../charts/mappers.js';
import { money } from '../format.js';

const props = defineProps({
  points: { type: Array, default: () => [] },
});

const chartData = computed(() => valueHistoryToChartData(props.points));

const options = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { intersect: false, mode: 'index' },
  plugins: {
    legend: { display: false },
    tooltip: { callbacks: { label: (ctx) => money(ctx.parsed.y) } },
  },
  scales: {
    y: { ticks: { callback: (v) => money(v) } },
  },
};
</script>

<template>
  <div v-if="points.length" class="chart-wrap">
    <Line :data="chartData" :options="options" />
  </div>
  <p v-else class="banner banner--muted">
    No value history yet — import transactions and seed some prices to populate this chart.
  </p>
</template>
