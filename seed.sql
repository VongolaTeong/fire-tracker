-- Fake demo seed data — INVENTED numbers, not real holdings. Safe to commit and to point
-- the public demo database at. Mirrors sample-transactions.csv so the CSV import and a
-- direct SQL seed produce the same portfolio.
--
-- Run against a local/demo database, e.g.:
--   psql "$DATABASE_URL" -f seed.sql
--
-- Idempotent: ON CONFLICT DO NOTHING means re-running never double-inserts (same guarantee
-- the import endpoint gives via external_id).

-- Reference instruments.
insert into instrument (ticker, name, currency, type) values
    ('VWRA', 'Vanguard FTSE All-World UCITS ETF', 'USD', 'ETF'),
    ('ES3',  'SPDR Straits Times Index ETF',      'SGD', 'ETF')
on conflict (ticker) do nothing;

-- Transaction ledger. external_id matches sample-transactions.csv for a coherent demo.
insert into transaction
    (external_id, ticker, type, quantity, price_per_unit, currency, fee, transaction_date) values
    ('vwra-2025-01',     'VWRA', 'BUY',        5.000000, 110.20, 'USD', 1.00, '2025-01-06'),
    ('vwra-2025-02',     'VWRA', 'BUY',        4.850000, 114.55, 'USD', 1.00, '2025-02-03'),
    ('vwra-2025-03',     'VWRA', 'BUY',        5.100000, 108.90, 'USD', 1.00, '2025-03-03'),
    ('vwra-2025-04',     'VWRA', 'BUY',        4.700000, 118.30, 'USD', 1.00, '2025-04-07'),
    ('vwra-2025-05',     'VWRA', 'BUY',        4.600000, 121.75, 'USD', 1.00, '2025-05-05'),
    ('vwra-2025-06',     'VWRA', 'BUY',        4.400000, 124.10, 'USD', 1.00, '2025-06-02'),
    ('vwra-2025-06-div', 'VWRA', 'DIVIDEND',  28.650000,   0.42, 'USD', 0.00, '2025-06-20'),
    ('vwra-2025-07',     'VWRA', 'BUY',        4.300000, 126.40, 'USD', 1.00, '2025-07-07'),
    ('vwra-2025-08',     'VWRA', 'BUY',        4.250000, 128.05, 'USD', 1.00, '2025-08-04'),
    ('es3-2025-02',      'ES3',  'BUY',      300.000000,   3.42, 'SGD', 2.50, '2025-02-12'),
    ('es3-2025-05',      'ES3',  'BUY',      280.000000,   3.55, 'SGD', 2.50, '2025-05-14'),
    ('es3-2025-08',      'ES3',  'BUY',      310.000000,   3.48, 'SGD', 2.50, '2025-08-13')
on conflict (external_id) where external_id is not null do nothing;
