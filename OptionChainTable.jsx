// ================= OPTION CELL =================
const OptionCell = ({ data, type }) => {
  if (!data) {
    return <td className="cell-empty">-</td>;
  }

  // ---------- SIGNAL BACKGROUND ----------
  const getSignalCellClass = (signal) => {
    switch (signal) {
      case "BULL":
        return "cell-bull";
      case "BEAR":
        return "cell-bear";
      case "PAUSED":
        return "cell-paused";
      default:
        return "";
    }
  };

  // ---------- SIGNAL COLOR ----------
  const getSignalClass = (signal) => {
    switch (signal) {
      case "BULL":
        return "signal-bull";
      case "BEAR":
        return "signal-bear";
      case "PAUSED":
        return "signal-paused";
      default:
        return "signal-default";
    }
  };

  // ---------- STRENGTH COLOR ----------
  const getStrengthClass = (strength = 0) => {
    if (strength >= 75) return "strength-high";
    if (strength >= 50) return "strength-medium";
    return "strength-low";
  };

  // ---------- MARKET COLOR ----------
  const getMarketClass = (state) =>
    state === "TRENDING" ? "market-trending" : "market-sideways";

  const cellClass = `option-cell ${
    type === "call" ? "cell-call" : "cell-put"
  } ${getSignalCellClass(data.signal)}`;

  return (
    <td className={cellClass}>
      <div className="option-grid">
        {/* PRICE */}
        <div className="option-field">
          <div className="option-field-label">Price</div>
          <div className="option-field-value option-ltp">
            {data.ltp?.toFixed(2)}
          </div>
        </div>

        {/* SIGNAL */}
        <div className="option-field">
          <div className="option-field-label">Signal</div>
          <div
            className={`option-field-value option-action ${getSignalClass(
              data.signal
            )}`}
          >
            {data.signal}
          </div>
        </div>

        {/* STRENGTH */}
        <div className="option-field">
          <div className="option-field-label">Strength</div>
          <div
            className={`option-field-value option-strength ${getStrengthClass(
              data.signalStrength
            )}`}
          >
            {data.signalStrength || 0}%
          </div>
        </div>

        {/* MARKET STATE */}
        <div className="option-field">
          <div className="option-field-label">Market</div>
          <div
            className={`option-field-value option-market ${getMarketClass(
              data.marketState
            )}`}
          >
            {data.marketState || "-"}
          </div>
        </div>
      </div>
    </td>
  );
};

// ================= CHAIN SUMMARY BAR =================
const ChainSummaryBar = ({ chainData }) => {
  // Compute averages for calls
  const calls = chainData.map((r) => r.call).filter(Boolean);
  const puts = chainData.map((r) => r.put).filter(Boolean);

  const avg = (arr, key) => {
    const valid = arr.map((x) => x[key]).filter((v) => v != null && !isNaN(v));
    if (!valid.length) return null;
    return valid.reduce((a, b) => a + b, 0) / valid.length;
  };

  const callAvgPrice = avg(calls, "ltp");
  const callAvgStrength = avg(calls, "signalStrength");
  const putAvgPrice = avg(puts, "ltp");
  const putAvgStrength = avg(puts, "signalStrength");

  const getStrengthClass = (strength) => {
    if (strength == null) return "strength-low";
    if (strength >= 75) return "strength-high";
    if (strength >= 50) return "strength-medium";
    return "strength-low";
  };

  return (
    <div className="chain-summary-bar">
      {/* CALL SUMMARY */}
      <div className="summary-side summary-call">
        <div className="summary-label">CALL AVG</div>
        <div className="summary-stats">
          <div className="summary-stat">
            <span className="summary-stat-label">Avg Price</span>
            <span className="summary-stat-value summary-price">
              {callAvgPrice != null ? callAvgPrice.toFixed(2) : "-"}
            </span>
          </div>
          <div className="summary-divider" />
          <div className="summary-stat">
            <span className="summary-stat-label">Avg Strength</span>
            <span
              className={`summary-stat-value ${getStrengthClass(
                callAvgStrength
              )}`}
            >
              {callAvgStrength != null ? `${callAvgStrength.toFixed(1)}%` : "-"}
            </span>
          </div>
        </div>
      </div>

      <div className="summary-center-divider" />

      {/* PUT SUMMARY */}
      <div className="summary-side summary-put">
        <div className="summary-label">PUT AVG</div>
        <div className="summary-stats">
          <div className="summary-stat">
            <span className="summary-stat-label">Avg Price</span>
            <span className="summary-stat-value summary-price">
              {putAvgPrice != null ? putAvgPrice.toFixed(2) : "-"}
            </span>
          </div>
          <div className="summary-divider" />
          <div className="summary-stat">
            <span className="summary-stat-label">Avg Strength</span>
            <span
              className={`summary-stat-value ${getStrengthClass(
                putAvgStrength
              )}`}
            >
              {putAvgStrength != null ? `${putAvgStrength.toFixed(1)}%` : "-"}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

// ================= OPTION CHAIN TABLE =================
const OptionChainTable = ({ indexName, chainData, indexData }) => {
  if (!chainData || chainData.length === 0) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <div className="loading-title">Loading {indexName} options...</div>
        <div className="loading-subtitle">Waiting for data...</div>
      </div>
    );
  }

  const atmStrike = indexData?.ltp
    ? Math.round(indexData.ltp / (indexName === "NIFTY" ? 50 : 100)) *
      (indexName === "NIFTY" ? 50 : 100)
    : null;

  return (
    <div className="option-chain-container">
      <div className="option-chain-header">
        <h3 className="option-chain-title">
          {indexName} Options : {indexData.ltp}
        </h3>
      </div>

      {/* ── SUMMARY BAR ── */}
      <ChainSummaryBar chainData={chainData} />

      <div className="option-chain-table-wrapper">
        <table className="option-chain-table">
          <thead>
            <tr>
              <th className="th-call">CALL</th>
              <th className="th-strike">STRIKE</th>
              <th className="th-put">PUT</th>
            </tr>
          </thead>

          <tbody>
            {chainData.map((row) => {
              const isATM = row.strike === atmStrike;
              const rowClass = isATM ? "row-atm" : "";

              return (
                <tr key={row.strike} className={rowClass}>
                  <OptionCell data={row.call} type="call" />

                  <td
                    className={isATM ? "strike-cell strike-atm" : "strike-cell"}
                  >
                    {row.strike}
                  </td>

                  <OptionCell data={row.put} type="put" />
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default OptionChainTable;

/*
  ── SUGGESTED CSS TO ADD ──────────────────────────────────────────────────────

  .chain-summary-bar {
    display: flex;
    align-items: stretch;
    background: #f8f9fc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    margin: 0 0 12px 0;
    overflow: hidden;
  }

  .summary-side {
    flex: 1;
    padding: 12px 20px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .summary-call { background: #f0fff4; }
  .summary-put  { background: #fff5f5; }

  .summary-label {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: #718096;
    text-transform: uppercase;
  }

  .summary-stats {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .summary-stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .summary-stat-label {
    font-size: 10px;
    color: #a0aec0;
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  .summary-stat-value {
    font-size: 18px;
    font-weight: 700;
    color: #2d3748;
  }

  .summary-price { color: #2d3748; }

  .summary-divider {
    width: 1px;
    height: 36px;
    background: #e2e8f0;
  }

  .summary-center-divider {
    width: 1px;
    background: #e2e8f0;
  }

  ─────────────────────────────────────────────────────────────────────────────
*/
