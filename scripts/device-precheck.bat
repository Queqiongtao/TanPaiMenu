@echo off
rem TanPaiMenu Phase 0 - collect tablet capability baseline into docs\device-baseline.md.
title TanPaiMenu - device precheck
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0device-precheck.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] Baseline written to docs\device-baseline.md) else (echo [FAILED] exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . .
    pause >nul
)
exit /b %RC%