@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   Сборка автономного PcRemote.exe для Windows
echo ========================================================
echo.

where pyinstaller >nul 2>&1
if errorlevel 1 (
    echo [1/2] Установка PyInstaller...
    pip install --upgrade pyinstaller
)

echo.
echo [2/2] Компиляция в один файл PcRemote.exe без консоли...
pyinstaller --onefile --noconsole --name "PcRemote" server.py

echo.
if exist "dist\PcRemote.exe" (
    echo ========================================================
    echo   [OK] Успешно собран: dist\PcRemote.exe
    echo   Его можно запускать двойным кликом на любом ПК без консоли!
    echo ========================================================
) else (
    echo [!] Ошибка компиляции.
)
pause
