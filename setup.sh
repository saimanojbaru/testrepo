#!/bin/bash
set -e

echo "=== Scalping Agent Setup ==="

# 1. Check Python version
python_version=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
if [[ $(echo "$python_version < 3.11" | bc) -eq 1 ]]; then
    echo "ERROR: Python 3.11+ required. You have $python_version"
    exit 1
fi
echo "✓ Python $python_version"

# 2. Create directories
mkdir -p data/{raw,processed}
mkdir -p logs
mkdir -p cache
echo "✓ Created data/, logs/, cache/ directories"

# 3. Install dependencies
if ! command -v pip &> /dev/null; then
    echo "ERROR: pip not found. Install Python 3.11+ with pip."
    exit 1
fi

echo "Installing dependencies..."
pip install -q -e . 2>/dev/null || {
    echo "WARNING: pip install failed. Try: pip install pandas numpy requests optuna scikit-learn pytz"
    exit 1
}
echo "✓ Dependencies installed"

# 4. Check .env
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✓ Created .env from template"
    echo ""
    echo "⚠️  NEXT: Edit .env with your Upstox API credentials"
    echo "   1. Go to https://developer.upstox.com"
    echo "   2. Create an app → copy API Key + Secret"
    echo "   3. Edit .env: UPSTOX_API_KEY=... UPSTOX_SECRET_KEY=..."
else
    echo "✓ .env already exists"
fi

# 5. Run smoke tests
echo ""
echo "Running smoke tests..."
python -m tests.test_costs 2>/dev/null && echo "✓ Cost model test passed" || echo "⚠️  Cost test failed"
python -m tests.test_risk 2>/dev/null && echo "✓ Risk engine test passed" || echo "⚠️  Risk test failed"
python -m tests.test_e2e 2>/dev/null && echo "✓ End-to-end test passed" || echo "⚠️  E2E test failed"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. Edit .env with Upstox credentials"
echo "  2. Run: python main.py --mode ingest --years 5"
echo "  3. Run: python main.py --mode discover --n-trials 100"
echo ""
echo "See QUICKSTART.md for detailed instructions."
