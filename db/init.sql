-- TimescaleDB schema for scalping agent
-- Creates hypertables for time-series data

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Instruments registry
CREATE TABLE IF NOT EXISTS instruments (
    instrument_key TEXT PRIMARY KEY,
    symbol TEXT NOT NULL,
    exchange TEXT NOT NULL,
    segment TEXT,               -- 'index', 'option', 'future', 'equity'
    instrument_type TEXT,       -- 'CE', 'PE', 'FUT', 'EQ'
    expiry DATE,
    strike DOUBLE PRECISION,
    lot_size INTEGER,
    tick_size DOUBLE PRECISION,
    underlying TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_instruments_symbol ON instruments(symbol);
CREATE INDEX idx_instruments_expiry ON instruments(expiry);
CREATE INDEX idx_instruments_underlying ON instruments(underlying);

-- 1-minute OHLCV bars (hypertable)
CREATE TABLE IF NOT EXISTS ohlcv_1m (
    timestamp   TIMESTAMPTZ NOT NULL,
    instrument_key TEXT NOT NULL,
    symbol      TEXT NOT NULL,
    open        DOUBLE PRECISION,
    high        DOUBLE PRECISION,
    low         DOUBLE PRECISION,
    close       DOUBLE PRECISION,
    volume      BIGINT,
    oi          BIGINT,
    PRIMARY KEY (timestamp, instrument_key)
);

SELECT create_hypertable('ohlcv_1m', 'timestamp', if_not_exists => TRUE);
CREATE INDEX idx_ohlcv_symbol ON ohlcv_1m(symbol, timestamp DESC);

-- Daily OHLCV
CREATE TABLE IF NOT EXISTS ohlcv_1d (
    trade_date  DATE NOT NULL,
    instrument_key TEXT NOT NULL,
    symbol      TEXT NOT NULL,
    open        DOUBLE PRECISION,
    high        DOUBLE PRECISION,
    low         DOUBLE PRECISION,
    close       DOUBLE PRECISION,
    settle      DOUBLE PRECISION,
    volume      BIGINT,
    oi          BIGINT,
    PRIMARY KEY (trade_date, instrument_key)
);

CREATE INDEX idx_ohlcv_1d_symbol ON ohlcv_1d(symbol, trade_date DESC);

-- Options chain snapshot (per minute)
CREATE TABLE IF NOT EXISTS options_chain_snapshot (
    timestamp   TIMESTAMPTZ NOT NULL,
    underlying  TEXT NOT NULL,
    expiry      DATE NOT NULL,
    strike      DOUBLE PRECISION NOT NULL,
    option_type TEXT NOT NULL, -- 'CE' or 'PE'
    ltp         DOUBLE PRECISION,
    iv          DOUBLE PRECISION,
    oi          BIGINT,
    volume      BIGINT,
    delta       DOUBLE PRECISION,
    gamma       DOUBLE PRECISION,
    theta       DOUBLE PRECISION,
    vega        DOUBLE PRECISION,
    PRIMARY KEY (timestamp, underlying, expiry, strike, option_type)
);

SELECT create_hypertable('options_chain_snapshot', 'timestamp', if_not_exists => TRUE);
CREATE INDEX idx_chain_underlying ON options_chain_snapshot(underlying, timestamp DESC);

-- Tick data (live stream)
CREATE TABLE IF NOT EXISTS ticks (
    timestamp   TIMESTAMPTZ NOT NULL,
    instrument_key TEXT NOT NULL,
    ltp         DOUBLE PRECISION,
    bid         DOUBLE PRECISION,
    ask         DOUBLE PRECISION,
    bid_qty     INTEGER,
    ask_qty     INTEGER,
    volume      BIGINT,
    oi          BIGINT,
    PRIMARY KEY (timestamp, instrument_key)
);

SELECT create_hypertable('ticks', 'timestamp', if_not_exists => TRUE);

-- Trade log (agent decisions + executions)
CREATE TABLE IF NOT EXISTS trade_log (
    trade_id    BIGSERIAL PRIMARY KEY,
    timestamp   TIMESTAMPTZ NOT NULL,
    instrument_key TEXT NOT NULL,
    symbol      TEXT,
    side        TEXT,           -- 'buy', 'sell'
    quantity    INTEGER,
    price       DOUBLE PRECISION,
    regime      TEXT,            -- regime label at decision time
    strategy    TEXT,            -- which strategy fired
    order_id    TEXT,            -- broker order id
    status      TEXT,            -- 'pending', 'filled', 'cancelled'
    pnl         DOUBLE PRECISION,
    costs       JSONB,           -- breakdown
    paper_mode  BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_trade_log_timestamp ON trade_log(timestamp DESC);
CREATE INDEX idx_trade_log_strategy ON trade_log(strategy);
