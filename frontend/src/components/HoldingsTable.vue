<script setup>
import { computed } from 'vue';
import { moneyPrecise, quantity } from '../format.js';

const props = defineProps({
  holdings: { type: Array, default: () => [] },
  positions: { type: Array, default: () => [] },
});

// Join holdings with the valued positions (when available) so the table can show market value.
const rows = computed(() => {
  const valueByTicker = new Map(props.positions.map((p) => [p.ticker, p.marketValueSgd]));
  return props.holdings.map((h) => ({
    ticker: h.ticker,
    units: h.units,
    currency: h.currency,
    marketValueSgd: valueByTicker.get(h.ticker) ?? null,
  }));
});
</script>

<template>
  <div v-if="rows.length" class="card">
    <table>
      <thead>
        <tr>
          <th>Instrument</th>
          <th>Units</th>
          <th>Currency</th>
          <th>Market value (SGD)</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.ticker">
          <td>{{ row.ticker }}</td>
          <td>{{ quantity(row.units) }}</td>
          <td>{{ row.currency }}</td>
          <td>{{ row.marketValueSgd == null ? '—' : moneyPrecise(row.marketValueSgd) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else class="banner banner--muted">No open holdings.</p>
</template>
