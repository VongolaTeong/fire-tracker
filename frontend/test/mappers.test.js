import { describe, it, expect } from 'vitest';
import { projectionToFanData, valueHistoryToChartData } from '../src/charts/mappers.js';

describe('valueHistoryToChartData', () => {
  it('maps dates to labels and amounts to one dataset', () => {
    const points = [
      { date: '2025-01-31', valueSgd: 1050.0 },
      { date: '2025-02-28', valueSgd: 1100.0 },
    ];

    const data = valueHistoryToChartData(points);

    expect(data.labels).toEqual(['2025-01-31', '2025-02-28']);
    expect(data.datasets).toHaveLength(1);
    expect(data.datasets[0].data).toEqual([1050, 1100]);
  });

  it('coerces string amounts (BigDecimal as string) to numbers', () => {
    const data = valueHistoryToChartData([{ date: '2025-01-31', valueSgd: '1050.00' }]);
    expect(data.datasets[0].data).toEqual([1050]);
  });

  it('handles an empty series', () => {
    const data = valueHistoryToChartData([]);
    expect(data.labels).toEqual([]);
    expect(data.datasets[0].data).toEqual([]);
  });
});

describe('projectionToFanData', () => {
  const projection = {
    targetDate: '2027-06-15',
    bands: [
      { date: '2026-06-15', monthsFromNow: 0, p10: 10000, p50: 10000, p90: 10000 },
      { date: '2027-06-15', monthsFromNow: 12, p10: 11000, p50: 13000, p90: 15000 },
    ],
  };

  it('produces p10, p90 and median datasets in order', () => {
    const data = projectionToFanData(projection);

    expect(data.labels).toEqual(['2026-06-15', '2027-06-15']);
    expect(data.datasets.map((d) => d.label)).toEqual([
      'p10 (pessimistic)',
      'p10–p90 range',
      'Median (p50)',
    ]);
    expect(data.datasets[0].data).toEqual([10000, 11000]); // p10
    expect(data.datasets[1].data).toEqual([10000, 15000]); // p90
    expect(data.datasets[2].data).toEqual([10000, 13000]); // p50
  });

  it('shades the band by filling the p90 line down to the p10 line', () => {
    const data = projectionToFanData(projection);
    // fill: '-1' targets the immediately-preceding dataset (p10), so the area between p10 and p90 is shaded.
    expect(data.datasets[1].fill).toBe('-1');
    expect(data.datasets[0].fill).toBe(false);
    expect(data.datasets[2].fill).toBe(false);
  });

  it('handles a missing/empty projection without throwing', () => {
    expect(projectionToFanData(undefined).labels).toEqual([]);
    expect(projectionToFanData({}).datasets[0].data).toEqual([]);
    expect(projectionToFanData({ bands: [] }).labels).toEqual([]);
  });
});
