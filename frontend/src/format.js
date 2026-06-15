// Display formatters. Amounts arrive as JSON numbers (BigDecimal serialized) — coerce defensively.

const sgd0 = new Intl.NumberFormat('en-SG', {
  style: 'currency',
  currency: 'SGD',
  maximumFractionDigits: 0,
});

const sgd2 = new Intl.NumberFormat('en-SG', {
  style: 'currency',
  currency: 'SGD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const units = new Intl.NumberFormat('en-SG', { maximumFractionDigits: 6 });

export function money(value) {
  if (value == null) return '—';
  return sgd0.format(Number(value));
}

export function moneyPrecise(value) {
  if (value == null) return '—';
  return sgd2.format(Number(value));
}

export function quantity(value) {
  if (value == null) return '—';
  return units.format(Number(value));
}

export function percent(fraction) {
  if (fraction == null) return '—';
  return `${(Number(fraction) * 100).toFixed(2)}%`;
}
