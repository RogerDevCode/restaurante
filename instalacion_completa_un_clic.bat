@echo off
setlocal EnableDelayedExpansion
title Instalador Todo en Uno - Restaurante 2026
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo     SISTEMA DE RESTAURANTE 2026 - INSTALACION 1-CLIC
echo ========================================================
echo.

:: Verificar privilegios de Administrador para permitir configuración completa del sistema si es posible
net session >nul 2>&1
if %errorlevel% neq 0 (
    if "%~1" neq "--no-elevate" (
        echo [INFO] Solicitando permisos de Administrador para configuracion completa del sistema...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "$proc = Start-Process cmd -ArgumentList '/c \"\"%~f0\"\" --elevated' -Verb RunAs -PassThru -ErrorAction SilentlyContinue; if ($proc) { exit 0 } else { exit 1 }"
        if !errorlevel! equ 0 (
            exit /b 0
        )
        echo [INFO] Continuando con permisos de usuario actual...
    )
)

:: Ejecutar instalador maestro PowerShell
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar_acceso_directo.ps1"

if %errorlevel% neq 0 (
    echo.
    echo [AVISO] El instalador finalizó con observaciones. Presione cualquier tecla para cerrar.
    pause >nul
)
