@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ========================================================
echo   Auto-Sync to GitHub (PC Remote)
echo ========================================================
echo.

where git >nul 2>&1
if errorlevel 1 (
    echo [!] Git is not installed on PC. Download from: https://git-scm.com/
    pause
    exit /b 1
)

echo [1/3] Adding changes...
git add -A

echo [2/3] Committing...
git commit -m "Auto-update v3.5 (1080p, gesture mouse, FPS toggle, APPS order)" >nul 2>&1

echo [3/3] Synchronizing with GitHub...
set ATTEMPT=1
:push_retry
echo Sending changes to GitHub (attempt !ATTEMPT! of 5)...
git pull --rebase origin main >nul 2>&1
git push origin main
if %errorlevel% equ 0 goto push_success

git push --force-with-lease origin main
if %errorlevel% equ 0 goto push_success

set /a ATTEMPT+=1
if !ATTEMPT! leq 5 (
    echo [!] Temporary GitHub error (Internal Server Error or network glitch).
    echo     Retrying in 4 seconds...
    timeout /t 4 >nul
    goto push_retry
)

echo.
echo [!] Could not push to GitHub after 5 attempts.
echo     Please check your internet connection and GitHub repository permissions.
pause
exit /b 1

:push_success
echo.
echo ========================================================
echo   [OK] Success! Changes pushed to GitHub!
echo   GitHub Actions is now compiling the new APK.
echo ========================================================
pause
