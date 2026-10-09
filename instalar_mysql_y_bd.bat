@echo off
setlocal
title Instalador de base nueva - Restaurante 2026
cd /d "%~dp0"

net session >nul 2>&1
if errorlevel 1 (
    echo [INFO] Se requieren permisos de Administrador para iniciar el servicio MySQL.
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs -Wait -PassThru -ErrorAction Stop; exit $p.ExitCode"
    exit /b %errorlevel%
)

if not exist "%~dp0instalar_mysql_y_bd.ps1" (
    echo [ERROR] No se encontro instalar_mysql_y_bd.ps1.
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar_mysql_y_bd.ps1"
set "INSTALLER_EXIT=%errorlevel%"
if not "%INSTALLER_EXIT%"=="0" echo [ERROR] Instalacion detenida. No se sobrescribio una base existente.
echo.
pause
exit /b %INSTALLER_EXIT%
