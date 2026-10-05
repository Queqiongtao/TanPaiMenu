@echo off
rem TanPaiMenu Phase 0 - install JDK 17 + Android SDK + Gradle (no admin required).
rem All parameters are forwarded to setup-toolchain.ps1.
title TanPaiMenu - setup toolchain
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-toolchain.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] Toolchain ready. Next run scripts\device-precheck.bat) else (echo [FAILED] exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . .
    pause >nul
)
exit /b %RC%