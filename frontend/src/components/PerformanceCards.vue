<script setup>
import { computed } from 'vue';
import { money, percent } from '../format.js';

const props = defineProps({
  performance: { type: Object, default: null },
});

const pnlClass = computed(() => {
  const pnl = Number(props.performance?.unrealizedPnl ?? 0);
  if (pnl > 0) return 'kpi__value kpi__value--pos';
  if (pnl < 0) return 'kpi__value kpi__value--neg';
  return 'kpi__value';
});
</script>

<template>
  <div v-if="performance" class="grid grid--cards">
    <div class="card">
      <div class="kpi__label">Total invested</div>
      <div class="kpi__value">{{ money(performance.totalInvested) }}</div>
    </div>
    <div class="card">
      <div class="kpi__label">Current value</div>
      <div class="kpi__value">{{ money(performance.currentValue) }}</div>
    </div>
    <div class="card">
      <div class="kpi__label">Unrealized P/L</div>
      <div :class="pnlClass">{{ money(performance.unrealizedPnl) }}</div>
    </div>
    <div class="card">
      <div class="kpi__label">XIRR</div>
      <div class="kpi__value">{{ percent(performance.xirr) }}</div>
    </div>
    <div class="card">
      <div class="kpi__label">CAGR</div>
      <div class="kpi__value">{{ percent(performance.cagr) }}</div>
    </div>
  </div>
  <p v-else class="banner banner--muted">Performance unavailable.</p>
</template>
