#!/bin/bash
# Launch the Scalping Agent dashboard
# Usage: bash run_dashboard.sh

set -e

echo "=== Starting Scalping Agent Dashboard ==="

# Check streamlit
if ! python -c "import streamlit" 2>/dev/null; then
    echo "Installing streamlit..."
    pip install -q streamlit plotly
fi

# Launch
echo ""
echo "Dashboard will open at http://localhost:8501"
echo "Press Ctrl+C to stop"
echo ""

streamlit run monitor/dashboard.py --server.headless=false --browser.gatherUsageStats=false
