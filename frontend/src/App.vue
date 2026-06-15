<script setup>
import { computed, onMounted, ref } from 'vue';
import { api } from './api/client.js';
import { money } from './format.js';
import PerformanceCards from './components/PerformanceCards.vue';
import ValueSummary from './components/ValueSummary.vue';
import HoldingsTable from './components/HoldingsTable.vue';
import ValueOverTimeChart from './components/ValueOverTimeChart.vue';
import ProjectionFanChart from './components/ProjectionFanChart.vue';

const loading = ref(true);
const loadError = ref('');

const value = ref(null);
const performance = ref(null);
const holdings = ref([]);
const valueHistory = ref([]);

const projection = ref(null);
const projecting = ref(false);
const projectionError = ref('');
const targetDate = ref(defaultTargetDate());

// Default the FIRE horizon ~15 years out, matching the project's framing.
function defaultTargetDate() {
  const d = new Date();
  d.setFullYear(d.getFullYear() + 15);
  return d.toISOString().slice(0, 10);
}

const terminal = computed(() => {
  const bands = projection.value?.bands;
  return bands && bands.length ? bands[bands.length - 1] : null;
});

async function loadProjection() {
  projecting.value = true;
  projectionError.value = '';
  try {
    projection.value = await api.projection(targetDate.value);
  } catch (err) {
    projection.value = null;
    projectionError.value = err.message;
  } finally {
    projecting.value = false;
  }
}

async function loadAll() {
  loading.value = true;
  loadError.value = '';
  // Load each panel independently — a single missing-data 422 (e.g. no price for one holding)
  // shouldn't blank the whole dashboard.
  const [v, p, h, vh] = await Promise.allSettled([
    api.value(),
    api.performance(),
    api.holdings(),
    api.valueHistory(),
  ]);
  value.value = v.status === 'fulfilled' ? v.value : null;
  performance.value = p.status === 'fulfilled' ? p.value : null;
  holdings.value = h.status === 'fulfilled' ? h.value : [];
  valueHistory.value = vh.status === 'fulfilled' ? vh.value : [];

  // If everything failed, the backend is probably unreachable — show one clear message.
  if ([v, p, h, vh].every((r) => r.status === 'rejected')) {
    loadError.value = `Couldn't reach the API at ${api.baseUrl}. Is the backend running? (${v.reason?.message ?? ''})`;
  }

  await loadProjection();
  loading.value = false;
}

onMounted(loadAll);
</script>

<template>
  <div class="app">
    <header class="app__header">
      <div>
        <h1 class="app__title">FIRE Portfolio Tracker</h1>
        <div class="app__subtitle">Dollar-cost-averaging analytics &amp; Monte Carlo retirement projection</div>
      </div>
      <div class="app__subtitle">API: {{ api.baseUrl }}</div>
    </header>

    <p v-if="loading" class="banner banner--muted">Loading portfolio…</p>
    <p v-else-if="loadError" class="banner banner--error">{{ loadError }}</p>

    <template v-if="!loading && !loadError">
      <section class="section">
        <h2 class="section__title">Performance</h2>
        <PerformanceCards :performance="performance" />
      </section>

      <section class="section">
        <h2 class="section__title">Current value</h2>
        <div class="grid" style="grid-template-columns: minmax(260px, 1fr) 2fr">
          <ValueSummary :value="value" />
          <HoldingsTable :holdings="holdings" :positions="value?.positions ?? []" />
        </div>
      </section>

      <section class="section">
        <h2 class="section__title">Portfolio value over time</h2>
        <div class="card">
          <ValueOverTimeChart :points="valueHistory" />
        </div>
      </section>

      <section class="section">
        <h2 class="section__title">FIRE projection</h2>
        <div class="controls">
          <label for="target">Target date</label>
          <input id="target" v-model="targetDate" type="date" @change="loadProjection" />
          <span v-if="projecting">projecting…</span>
          <span v-else-if="terminal">
            Median at {{ projection.targetDate }}:
            <strong>{{ money(terminal.p50) }}</strong>
            (p10 {{ money(terminal.p10) }} – p90 {{ money(terminal.p90) }})
          </span>
        </div>
        <p v-if="projectionError" class="banner banner--error">{{ projectionError }}</p>
        <div class="card">
          <ProjectionFanChart :projection="projection" />
        </div>
      </section>
    </template>
  </div>
</template>
