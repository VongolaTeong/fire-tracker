// Pure transforms from API payloads to Chart.js datasets. Kept free of Vue/Chart.js imports so
// they're trivially unit-testable (see test/mappers.test.js) — the most valuable thing to pin,
// since this is where API shape meets chart shape.

const BLUE = '#2563eb';
const GREEN = '#059669';
const GREEN_SOFT = 'rgba(16, 185, 129, 0.45)';
const GREEN_FILL = 'rgba(16, 185, 129, 0.15)';

/** GET /api/portfolio/value-history → a single-line "portfolio value over time" dataset. */
export function valueHistoryToChartData(points = []) {
  return {
    labels: points.map((p) => p.date),
    datasets: [
      {
        label: 'Portfolio value (SGD)',
        data: points.map((p) => Number(p.valueSgd)),
        borderColor: BLUE,
        backgroundColor: 'rgba(37, 99, 235, 0.12)',
        fill: true,
        tension: 0.25,
        pointRadius: 2,
      },
    ],
  };
}

/**
 * GET /api/portfolio/projection → a p10/p50/p90 "fan". The p90 dataset fills down to the p10
 * dataset immediately above it (`fill: '-1'`), shading the confidence band; the median is a
 * dashed line on top. Dataset order matters for the fill to target p10.
 */
export function projectionToFanData(projection) {
  const bands = projection?.bands ?? [];
  return {
    labels: bands.map((b) => b.date),
    datasets: [
      {
        label: 'p10 (pessimistic)',
        data: bands.map((b) => Number(b.p10)),
        borderColor: GREEN_SOFT,
        pointRadius: 0,
        fill: false,
      },
      {
        label: 'p10–p90 range',
        data: bands.map((b) => Number(b.p90)),
        borderColor: GREEN_SOFT,
        backgroundColor: GREEN_FILL,
        pointRadius: 0,
        fill: '-1',
      },
      {
        label: 'Median (p50)',
        data: bands.map((b) => Number(b.p50)),
        borderColor: GREEN,
        borderDash: [6, 4],
        pointRadius: 0,
        fill: false,
      },
    ],
  };
}
