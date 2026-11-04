@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   Cloudflare Tunnel Launcher (4G / Internet Remote)
echo ========================================================
echo.

where cloudflared >nul 2>&1
if not errorlevel 1 (
    set "CF_CMD=cloudflared"
    goto found
)

if exist "cloudflared.exe" (
    set "CF_CMD=.\cloudflared.exe"
    goto found
)

if exist "%ProgramFiles%\Cloudflare\cloudflared\cloudflared.exe" (
    set "CF_CMD="%ProgramFiles%\Cloudflare\cloudflared\cloudflared.exe""
    goto found
)

if exist "%ProgramFiles(x86)%\Cloudflare\cloudflared\cloudflared.exe" (
    set "CF_CMD="%ProgramFiles(x86)%\Cloudflare\cloudflared\cloudflared.exe""
    goto found
)

if exist "%ProgramFiles%\Cloudflare\cloudflared.exe" (
    set "CF_CMD="%ProgramFiles%\Cloudflare\cloudflared.exe""
    goto found
)

if exist "%ProgramFiles(x86)%\Cloudflare\cloudflared.exe" (
    set "CF_CMD="%ProgramFiles(x86)%\Cloudflare\cloudflared.exe""
    goto found
)

if exist "%USERPROFILE%\Downloads\cloudflared.exe" (
    set "CF_CMD="%USERPROFILE%\Downloads\cloudflared.exe""
    goto found
)

echo [!] cloudflared.exe не найден автоматически.
echo     Скачайте файл "Executable" (64-bit) с сайта Cloudflare
echo     и положите cloudflared.exe в эту же папку!
echo.
pause
exit /b 1

:found
echo [OK] Найден: %CF_CMD%
echo Запускаю защищенный интернет-туннель для порта 8765...
echo Ищите внизу строчку вида: https://xxxx.trycloudflare.com
echo.
%CF_CMD% tunnel --url http://127.0.0.1:8765
pause
