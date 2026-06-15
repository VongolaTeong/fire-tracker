<script setup>
import { moneyPrecise } from '../format.js';

defineProps({
  value: { type: Object, default: null },
});
</script>

<template>
  <div v-if="value" class="card">
    <div class="kpi__label">Portfolio value ({{ value.reportingCurrency }})</div>
    <div class="kpi__value">{{ moneyPrecise(value.totalValueSgd) }}</div>

    <table v-if="value.byCurrency?.length" style="margin-top: 14px">
      <thead>
        <tr>
          <th>Currency</th>
          <th>Local value</th>
          <th>Value (SGD)</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in value.byCurrency" :key="row.currency">
          <td>{{ row.currency }}</td>
          <td>{{ Number(row.marketValueLocal).toLocaleString() }}</td>
          <td>{{ moneyPrecise(row.marketValueSgd) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else class="banner banner--muted">Current value unavailable (missing price or FX data).</p>
</template>
