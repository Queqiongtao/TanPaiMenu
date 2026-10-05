@echo off
rem TanPaiMenu Phase 0 - build debug APK, install to the tablet and self-check.
title TanPaiMenu - build and install
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-and-install.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] APK built, installed and verified) else (echo [FAILED] exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . .
    pause >nul
)
exit /b %RC%