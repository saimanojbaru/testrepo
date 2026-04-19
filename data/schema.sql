-- TimescaleDB schema for scalping agent.
-- Idempotent: re-runnable during development.

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS instruments (
    instrument_key TEXT PRIMARY KEY,
    exchange       TEXT NOT NULL,
    symbol         TEXT NOT NULL,
    segment        TEXT NOT NULL,            -- INDEX | EQUITY | OPTION | FUTURE
    expiry         DATE,
    strike         NUMERIC(12, 2),
    option_type    TEXT CHECK (option_type IN ('CE', 'PE') OR option_type IS NULL),
    lot_size       INTEGER NOT NULL DEFAULT 1,
    tick_size      NUMERIC(8, 4) NOT NULL DEFAULT 0.05,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_instruments_symbol ON instruments (symbol);
CREATE INDEX IF NOT EXISTS idx_instruments_expiry ON instruments (expiry) WHERE expiry IS NOT NULL;

CREATE TABLE IF NOT EXISTS ohlcv_1m (
    ts             TIMESTAMPTZ NOT NULL,
    instrument_key TEXT        NOT NULL REFERENCES instruments(instrument_key),
    open           NUMERIC(12, 2) NOT NULL,
    high           NUMERIC(12, 2) NOT NULL,
    low            NUMERIC(12, 2) NOT NULL,
    close          NUMERIC(12, 2) NOT NULL,
    volume         BIGINT         NOT NULL DEFAULT 0,
    open_interest  BIGINT,
    PRIMARY KEY (instrument_key, ts)
);

SELECT create_hypertable('ohlcv_1m', 'ts', if_not_exists => TRUE);

CREATE TABLE IF NOT EXISTS ohlcv_daily (
    ts             DATE NOT NULL,
    instrument_key TEXT NOT NULL REFERENCES instruments(instrument_key),
    open           NUMERIC(12, 2) NOT NULL,
    high           NUMERIC(12, 2) NOT NULL,
    low            NUMERIC(12, 2) NOT NULL,
    close          NUMERIC(12, 2) NOT NULL,
    volume         BIGINT         NOT NULL DEFAULT 0,
    open_interest  BIGINT,
    PRIMARY KEY (instrument_key, ts)
);

CREATE TABLE IF NOT EXISTS options_chain_snapshot (
    ts             TIMESTAMPTZ NOT NULL,
    underlying     TEXT        NOT NULL,
    expiry         DATE        NOT NULL,
    strike         NUMERIC(12, 2) NOT NULL,
    option_type    TEXT        NOT NULL CHECK (option_type IN ('CE', 'PE')),
    ltp            NUMERIC(12, 2),
    bid            NUMERIC(12, 2),
    ask            NUMERIC(12, 2),
    iv             NUMERIC(8, 4),
    delta          NUMERIC(8, 4),
    gamma          NUMERIC(10, 6),
    theta          NUMERIC(10, 4),
    vega           NUMERIC(10, 4),
    open_interest  BIGINT,
    volume         BIGINT,
    PRIMARY KEY (underlying, expiry, strike, option_type, ts)
);

SELECT create_hypertable('options_chain_snapshot', 'ts', if_not_exists => TRUE);

CREATE TABLE IF NOT EXISTS ticks (
    ts             TIMESTAMPTZ NOT NULL,
    instrument_key TEXT        NOT NULL REFERENCES instruments(instrument_key),
    ltp            NUMERIC(12, 2) NOT NULL,
    bid            NUMERIC(12, 2),
    ask            NUMERIC(12, 2),
    bid_qty        INTEGER,
    ask_qty        INTEGER,
    volume         BIGINT,
    open_interest  BIGINT
);

SELECT create_hypertable('ticks', 'ts', if_not_exists => TRUE, chunk_time_interval => INTERVAL '1 day');

CREATE INDEX IF NOT EXISTS idx_ticks_instrument_ts ON ticks (instrument_key, ts DESC);
