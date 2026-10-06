@echo off
rem TanPaiMenu - fetch and subset the display-page store-name font.
title TanPaiMenu - fetch store name font
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0fetch-store-name-font.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo [DONE] store name font ready) else (echo [FAILED] exit code %RC%)
rem Keep the window open when launched by double-click (cmd /c ...).
echo %cmdcmdline% | findstr /i /c:"/c" >nul
if not errorlevel 1 (
    echo.
    echo Press any key to close this window . . . 
    pause >nul
)
exit /b %RC%