@echo off
rem TanPaiMenu - collect device logcat and app logs for offline analysis.
title TanPaiMenu - collect logs
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0collect-logs.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] Logs collected under dist\logs) else (echo [FAILED] exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . . 
    pause >nul
)
exit /b %RC%
