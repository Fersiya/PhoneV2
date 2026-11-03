@echo off
setlocal
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
git add .

echo [2/3] Committing...
git commit -m "Auto-update (%date% %time%)" >nul 2>&1

echo [3/3] Pushing to GitHub...
git push origin main || git push --force-with-lease origin main
if errorlevel 1 (
    echo [!] Push failed. Check your git credentials or internet connection.
    pause
    exit /b 1
)

echo.
echo Success! Changes pushed to GitHub.
echo GitHub Actions has started building the new APK automatically!
pause
