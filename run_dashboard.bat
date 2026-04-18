@echo off
REM Windows launcher for Scalping Agent dashboard
REM Double-click this file or run from cmd/PowerShell

echo === Starting Scalping Agent Dashboard ===
echo.

REM Change to the directory this .bat file lives in (the repo root)
cd /d "%~dp0"

REM Install streamlit if missing (uses python -m to bypass PATH issues)
python -c "import streamlit" 2>nul
if errorlevel 1 (
    echo Installing streamlit...
    python -m pip install streamlit pandas numpy
)

echo.
echo Dashboard opening at http://localhost:8501
echo Press Ctrl+C to stop
echo.

REM Launch via python -m to avoid PATH issues with Microsoft Store Python
python -m streamlit run monitor/dashboard.py --browser.gatherUsageStats=false

pause
