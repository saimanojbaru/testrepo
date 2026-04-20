@echo off
REM Windows launcher for the mobile API backend.
REM Loads .env, starts uvicorn on 0.0.0.0:8000 so the phone on the LAN can connect.

setlocal
if exist .env (
    for /F "usebackq tokens=1,* delims==" %%A in (`type .env`) do (
        if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
    )
)

echo Backend listening on http://0.0.0.0:8000
python -m uvicorn mobile_api.server:app --host 0.0.0.0 --port 8000 --reload
endlocal
