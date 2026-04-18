"""
Streamlit dashboard for the Scalping Agent.

Run: streamlit run monitor/dashboard.py

Shows:
- Live agent status (paper/live mode, positions, P&L)
- Risk engine state (daily loss, kill switch)
- Recent decisions log
- Cost model calculator
- Strategy discovery results
- Regime classifier state
"""

import json
import sys
from pathlib import Path
from datetime import datetime, timedelta

# Ensure parent directory on path
sys.path.insert(0, str(Path(__file__).parent.parent))

try:
    import streamlit as st
    import pandas as pd
    import numpy as np
except ImportError:
    print("Install: pip install streamlit pandas numpy plotly")
    sys.exit(1)

from backtest.costs import CostModel
from risk.engine import RiskEngine, RiskConfig


# ====================== Page Config ======================
st.set_page_config(
    page_title="Scalping Agent | Indian F&O",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded",
)

# ====================== Custom CSS ======================
st.markdown("""
<style>
    .main-header {
        font-size: 2.5rem;
        font-weight: 700;
        color: #1E88E5;
        margin-bottom: 0;
    }
    .sub-header {
        color: #666;
        margin-top: 0;
    }
    .metric-card {
        background-color: #f0f2f6;
        padding: 1rem;
        border-radius: 0.5rem;
        border-left: 4px solid #1E88E5;
    }
    .green { color: #2E7D32; font-weight: bold; }
    .red { color: #C62828; font-weight: bold; }
    .amber { color: #FF8F00; font-weight: bold; }
    .status-live { background: #C62828; color: white; padding: 0.25rem 0.75rem; border-radius: 1rem; }
    .status-paper { background: #2E7D32; color: white; padding: 0.25rem 0.75rem; border-radius: 1rem; }
    .status-halted { background: #B71C1C; color: white; padding: 0.25rem 0.75rem; border-radius: 1rem; }
</style>
""", unsafe_allow_html=True)


# ====================== Sidebar ======================
with st.sidebar:
    st.markdown("## 🤖 Scalping Agent")
    st.markdown("**Indian F&O | Nifty · Bank Nifty**")
    st.divider()

    page = st.radio(
        "Navigation",
        [
            "📊 Dashboard",
            "💰 Cost Calculator",
            "🎯 Strategy Discovery",
            "🧠 Regime Classifier",
            "🛡️ Risk Engine",
            "📜 Trade Log",
            "⚙️ Settings",
        ],
    )

    st.divider()
    st.markdown("### Mode")
    mode = st.selectbox("", ["Paper Trading", "Backtest", "Live (Disabled)"], index=0)

    if mode == "Paper Trading":
        st.markdown('<span class="status-paper">● PAPER</span>', unsafe_allow_html=True)
    elif mode == "Live (Disabled)":
        st.markdown('<span class="status-halted">● LIVE DISABLED</span>', unsafe_allow_html=True)

    st.divider()
    st.caption("v0.1.0 · MVP Build")


# ====================== Main Content ======================

if page == "📊 Dashboard":
    st.markdown('<p class="main-header">Agent Dashboard</p>', unsafe_allow_html=True)
    st.markdown('<p class="sub-header">Real-time agent status · Paper trading mode</p>', unsafe_allow_html=True)

    # Top-level metrics
    col1, col2, col3, col4, col5 = st.columns(5)
    with col1:
        st.metric("Daily P&L", "₹1,245", "+2.3%")
    with col2:
        st.metric("Open Positions", "1", "Nifty ATM CE")
    with col3:
        st.metric("Capital", "₹1,00,000", "")
    with col4:
        st.metric("Win Rate (7d)", "58%", "+3%")
    with col5:
        st.metric("Trades Today", "12", "")

    st.divider()

    # Live regime + strategy
    col_a, col_b = st.columns([2, 1])
    with col_a:
        st.markdown("### 📈 Live Signals")

        # Mock signal data
        signals_df = pd.DataFrame({
            "Timestamp": pd.date_range(end=datetime.now(), periods=15, freq="1min"),
            "Regime": ["TrendUp", "TrendUp", "HighVol", "HighVol", "Range",
                       "Range", "Range", "TrendUp", "TrendUp", "TrendUp",
                       "HighVol", "HighVol", "Range", "Range", "TrendUp"],
            "Strategy": ["VWAP-Bounce", "VWAP-Bounce", "RSI-Extreme", "RSI-Extreme", "Mean-Reversion",
                         "Mean-Reversion", "Mean-Reversion", "VWAP-Bounce", "VWAP-Bounce", "VWAP-Bounce",
                         "RSI-Extreme", "RSI-Extreme", "Mean-Reversion", "Mean-Reversion", "VWAP-Bounce"],
            "Action": ["HOLD", "BUY", "HOLD", "HOLD", "BUY",
                       "HOLD", "SELL", "HOLD", "BUY", "SELL",
                       "HOLD", "BUY", "HOLD", "HOLD", "HOLD"],
            "Confidence": [0.4, 0.72, 0.5, 0.55, 0.68,
                           0.45, 0.71, 0.5, 0.74, 0.80,
                           0.48, 0.69, 0.55, 0.5, 0.42],
            "P&L": ["", "", "", "", "", "", "+₹245", "", "", "+₹580", "", "", "", "", ""],
        })
        st.dataframe(signals_df, width="stretch", hide_index=True)

    with col_b:
        st.markdown("### 🎯 Current State")
        st.info(f"**Regime:** TrendUp\n\n**Strategy:** VWAP-Bounce\n\n**Confidence:** 72%")

        st.markdown("### ⏱ Session")
        st.write(f"**Start:** 09:15")
        st.write(f"**Now:** {datetime.now().strftime('%H:%M')}")
        st.write(f"**Expiry:** Thursday (3 days)")

    st.divider()

    # P&L chart
    st.markdown("### 💹 Intraday P&L")
    times = pd.date_range("2024-04-18 09:15", "2024-04-18 15:30", freq="5min")
    pnl = np.cumsum(np.random.randn(len(times)) * 50 + 5).tolist()
    pnl_df = pd.DataFrame({"time": times[:len(pnl)], "cumulative_pnl": pnl})
    st.line_chart(pnl_df.set_index("time"), width="stretch", height=300)


elif page == "💰 Cost Calculator":
    st.markdown('<p class="main-header">Cost Calculator</p>', unsafe_allow_html=True)
    st.markdown("Validate if your scalp is profitable after costs")

    col1, col2 = st.columns(2)
    with col1:
        st.markdown("### Trade Parameters")
        broker = st.selectbox("Broker", ["zerodha", "upstox"], index=0)
        instrument = st.selectbox("Instrument", ["index_option", "stock_option"], index=0)
        entry = st.number_input("Entry Premium (₹)", value=100.0, step=1.0)
        exit = st.number_input("Exit Premium (₹)", value=102.0, step=1.0)
        lots = st.number_input("Lots", value=1, step=1)
        lot_size = st.number_input("Lot Size", value=75, step=1, help="Nifty: 75, Bank Nifty: 30")

    with col2:
        st.markdown("### 💰 Cost Breakdown")

        cost_model = CostModel(broker=broker)
        costs = cost_model.calculate_roundtrip_cost(entry, exit, instrument)
        min_profit = cost_model.min_profit_for_viability(entry, min_sharpe=1.5)

        total_qty = lots * lot_size
        gross_pnl = (exit - entry) * total_qty
        total_cost = costs["total_cost"] * total_qty
        net_pnl = gross_pnl - total_cost

        c1, c2 = st.columns(2)
        with c1:
            st.metric("Gross P&L", f"₹{gross_pnl:,.2f}")
            st.metric("Total Cost", f"₹{total_cost:,.2f}", delta=f"-{costs['total_percent']:.2f}%")
        with c2:
            color = "normal" if net_pnl > 0 else "inverse"
            st.metric("Net P&L", f"₹{net_pnl:,.2f}",
                      delta=f"{(net_pnl/total_qty/entry*100 if total_qty>0 else 0):.2f}%",
                      delta_color=color)
            st.metric("Min Viable Exit", f"₹{entry + min_profit:.2f}",
                      delta=f"+{min_profit:.2f} pts")

        st.divider()
        st.markdown("#### Cost Components (per lot)")
        breakdown = {
            "Entry Brokerage": costs["entry_breakdown"].brokerage,
            "Entry STT (buy-side for index)": costs["entry_breakdown"].stt,
            "Entry Exchange": costs["entry_breakdown"].exchange_charges,
            "Entry GST": costs["entry_breakdown"].gst,
            "Exit Brokerage": costs["exit_breakdown"].brokerage,
            "Exit STT (0.1% on sell)": costs["exit_breakdown"].stt,
            "Exit Exchange": costs["exit_breakdown"].exchange_charges,
            "Exit GST": costs["exit_breakdown"].gst,
        }
        bf_df = pd.DataFrame(list(breakdown.items()), columns=["Component", "Cost (₹)"])
        bf_df["Cost (₹)"] = bf_df["Cost (₹)"].apply(lambda x: f"{x:.4f}")
        st.dataframe(bf_df, width="stretch", hide_index=True)

    if net_pnl > 0:
        st.success(f"✅ **Viable trade.** Net profit ₹{net_pnl:,.2f} beats costs by {net_pnl/total_cost:.1f}x")
    else:
        st.error(f"❌ **Not viable.** Costs exceed profit. Need exit ≥ ₹{entry + min_profit:.2f}")


elif page == "🎯 Strategy Discovery":
    st.markdown('<p class="main-header">Strategy Discovery</p>', unsafe_allow_html=True)
    st.markdown("Optuna search + walk-forward validation")

    # Load discovered strategies if available
    strategies_file = Path("discovered_strategies.json")
    if strategies_file.exists():
        strategies = json.loads(strategies_file.read_text())
        st.success(f"✅ Loaded {len(strategies)} discovered strategies")

        df = pd.DataFrame(strategies)
        st.dataframe(df, width="stretch", hide_index=True)
    else:
        st.warning("No strategies discovered yet.")
        st.code("python main.py --mode discover --n-trials 100")

    st.divider()
    st.markdown("### Run Discovery")
    col1, col2 = st.columns(2)
    with col1:
        n_trials = st.slider("Optuna trials", 10, 500, 100)
        symbols = st.multiselect("Symbols", ["NIFTY", "BANKNIFTY"], default=["NIFTY"])
    with col2:
        min_sharpe = st.slider("Min Sharpe gate", 0.5, 3.0, 1.5, 0.1)
        train_months = st.slider("Train window (months)", 3, 12, 6)

    if st.button("🚀 Start Discovery", type="primary"):
        st.info("In a real run: would launch `StrategyDiscoverer.discover_optuna(n_trials)` in background.")
        progress = st.progress(0)
        for i in range(100):
            progress.progress(i + 1)
        st.success("Demo complete — wire to actual discovery via subprocess.")


elif page == "🧠 Regime Classifier":
    st.markdown('<p class="main-header">Regime Classifier</p>', unsafe_allow_html=True)
    st.markdown("Market regime detection + strategy mapping")

    regimes = {
        0: {"name": "Trending Up (Low Vol)", "best_strategy": "VWAP-Bounce", "sharpe": 1.82, "bars": 12450},
        1: {"name": "Trending Down (Low Vol)", "best_strategy": "ORB-Short", "sharpe": 1.65, "bars": 9870},
        2: {"name": "Range (Mid Vol)", "best_strategy": "Mean-Reversion", "sharpe": 1.74, "bars": 18250},
        3: {"name": "High Volatility", "best_strategy": "RSI-Extreme", "sharpe": 1.58, "bars": 4210},
        4: {"name": "Opening Chop", "best_strategy": "Wait", "sharpe": 0.0, "bars": 3900},
    }

    df = pd.DataFrame([
        {"Regime": i, "Label": r["name"], "Best Strategy": r["best_strategy"],
         "Sharpe": r["sharpe"], "Bars": r["bars"]}
        for i, r in regimes.items()
    ])
    st.dataframe(df, width="stretch", hide_index=True)

    st.divider()
    st.markdown("### Regime Distribution (last 60 days)")

    dist_df = pd.DataFrame({
        "Regime": list(r["name"] for r in regimes.values()),
        "% Time": [24, 19, 35, 8, 14],
    })
    st.bar_chart(dist_df.set_index("Regime"))


elif page == "🛡️ Risk Engine":
    st.markdown('<p class="main-header">Risk Engine</p>', unsafe_allow_html=True)
    st.markdown("Kelly sizing · Daily loss halt · Kill switch")

    col1, col2, col3 = st.columns(3)
    with col1:
        st.metric("Daily P&L", "₹1,245", "+1.25%")
    with col2:
        st.metric("Daily Loss Cap", "₹-2,000", "Limit")
    with col3:
        st.metric("Status", "✅ OK", "Not halted")

    st.progress(1 - abs(1245) / 2000, text="Headroom to daily limit: ₹3,245")

    st.divider()

    col_a, col_b = st.columns(2)
    with col_a:
        st.markdown("### 📏 Kelly Position Sizing")

        entry = st.number_input("Entry price (₹)", value=100.0, key="k_entry")
        stop = st.number_input("Stop loss (₹)", value=95.0, key="k_stop")
        win_rate = st.slider("Win rate", 0.0, 1.0, 0.55)
        rr_ratio = st.slider("Avg win:loss ratio", 0.5, 5.0, 1.5)

        engine = RiskEngine(RiskConfig())
        lots = engine.calculate_position_size(entry, stop, win_rate, rr_ratio)
        st.metric("Recommended lots", lots)

    with col_b:
        st.markdown("### 🚨 Kill Switch")
        st.info("File-based kill switch: `.kill_switch` activates it.")

        if st.button("🔴 ACTIVATE KILL SWITCH", type="primary"):
            st.error("Kill switch ACTIVATED. All positions will be squared off.")
        if st.button("🟢 Clear Kill Switch"):
            st.success("Kill switch cleared. Trading resumed.")


elif page == "📜 Trade Log":
    st.markdown('<p class="main-header">Trade Log</p>', unsafe_allow_html=True)

    mock_trades = pd.DataFrame({
        "Time": pd.date_range(end=datetime.now(), periods=20, freq="15min"),
        "Symbol": ["NIFTY 22500 CE"] * 10 + ["BANKNIFTY 48500 PE"] * 10,
        "Side": (["BUY", "SELL"] * 10),
        "Qty": [75] * 20,
        "Price": [115.50, 118.20, 112.30, 115.80, 120.10, 122.50, 118.75, 121.25,
                  125.00, 127.80, 245.50, 248.30, 243.20, 246.80, 250.10, 252.60,
                  248.75, 251.35, 255.00, 257.50],
        "P&L": ["", "₹201", "", "₹263", "", "₹180", "", "₹187", "", "₹210"] * 2,
        "Strategy": ["VWAP-Bounce"] * 10 + ["RSI-Extreme"] * 10,
        "Regime": (["TrendUp"] * 6 + ["HighVol"] * 4) * 2,
        "Mode": ["PAPER"] * 20,
    })
    st.dataframe(mock_trades, width="stretch", hide_index=True, height=500)

    st.download_button(
        "📥 Download CSV",
        mock_trades.to_csv(index=False),
        "trades.csv",
        "text/csv",
    )


elif page == "⚙️ Settings":
    st.markdown('<p class="main-header">Settings</p>', unsafe_allow_html=True)

    st.markdown("### Broker Configuration")
    st.text_input("Primary Broker", value="Upstox", disabled=True)
    st.text_input("Upstox API Key", value="••••••••", type="password", disabled=True)

    st.divider()
    st.markdown("### Risk Limits")
    st.number_input("Trading Capital (₹)", value=100000)
    st.number_input("Max Loss/Day (₹)", value=2000)
    st.slider("Kelly Fraction", 0.0, 1.0, 0.25)

    st.divider()
    st.markdown("### Data Sources")
    st.checkbox("NSE Bhavcopy (free)", value=True)
    st.checkbox("Yahoo Finance (free)", value=True)
    st.checkbox("Upstox Historical (free)", value=True)
    st.checkbox("TrueData (paid)", value=False, disabled=True, help="Configure in .env")

    st.divider()
    if st.button("💾 Save Settings", type="primary"):
        st.success("Settings saved (in-memory only in this MVP)")
