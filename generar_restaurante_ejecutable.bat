@echo off
setlocal
title Generador de Restaurante_Ejecutable (Windows 11)
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   REGENERADOR Y EMPAQUETADOR DE RESTAURANTE_EJECUTABLE
echo ========================================================
echo.

where powershell >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] No se encontro PowerShell en el sistema.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0generar_restaurante_ejecutable.ps1" %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] El proceso de empaquetado finalizo con errores.
    pause
    exit /b %ERRORLEVEL%
)

echo.
pause
endlocal
