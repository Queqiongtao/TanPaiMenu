@echo off
rem TanPaiMenu Phase 0 - read-only toolchain self check.
title TanPaiMenu - doctor
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0doctor.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] All checks passed) else (echo [FAILED] problems found, exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . .
    pause >nul
)
exit /b %RC%