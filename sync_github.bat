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
git add -A

echo [2/3] Committing...
git commit -m "Auto-update (%date% %time%)" >nul 2>&1

echo [3/3] Synchronizing with GitHub...
git pull --rebase origin main >nul 2>&1
git push origin main
if %errorlevel% equ 0 goto push_success

echo.
echo [!] Normal push failed. Trying push with force...
git push --force origin main
if %errorlevel% equ 0 goto push_success

echo.
echo [!] Push failed. Check your internet connection or GitHub repository.
pause
exit /b 1

:push_success
echo.
echo ========================================================
echo   [OK] Success! Changes pushed to GitHub!
echo   GitHub Actions is now compiling the new APK.
echo ========================================================
pause
