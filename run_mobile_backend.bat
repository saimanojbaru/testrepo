@echo off
REM Windows launcher for the Scalping Agent mobile backend (FastAPI)
REM Double-click this file or run from cmd/PowerShell.

echo === Starting Scalping Agent Mobile Backend ===
echo.

REM Change to the directory this .bat file lives in (the repo root)
cd /d "%~dp0"

REM Install required packages if missing. Uses python -m to bypass PATH issues.
python -c "import fastapi, uvicorn, dotenv" 2>nul
if errorlevel 1 (
    echo Installing FastAPI + dependencies...
    python -m pip install fastapi "uvicorn[standard]" python-dotenv
)

REM Firebase admin is optional (push notifications). Comment this block out if
REM you're not using push yet.
python -c "import firebase_admin" 2>nul
if errorlevel 1 (
    echo Installing firebase-admin (optional, for push)...
    python -m pip install firebase-admin 1>nul 2>nul
)

REM Load .env so MOBILE_API_SECRET etc. are visible to uvicorn.
if not exist .env (
    echo WARNING: .env is missing. Copy .env.example to .env and fill in
    echo          MOBILE_API_SECRET and MOBILE_API_SHARED_SECRET before launching.
    pause
    exit /b 1
)

echo.
echo Backend listening on http://0.0.0.0:8000
echo Phone should connect to http://<this-PC-LAN-IP>:8000
echo Press Ctrl+C to stop.
echo.

python -m uvicorn mobile_api.server:app --host 0.0.0.0 --port 8000

pause
