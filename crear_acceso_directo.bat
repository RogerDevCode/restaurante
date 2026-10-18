@echo off
setlocal
title Crear Acceso Directo en el Escritorio - Restaurante 2026
cd /d "%~dp0"

echo ========================================================
echo   CREANDO ACCESO DIRECTO EN EL ESCRITORIO DE WINDOWS
echo   Restaurante 2026
echo ========================================================
echo.

set "VBS_SCRIPT=%~dp0iniciar_restaurante_silencioso.vbs"
set "ICO_FILE=%~dp0icono_restaurante.ico"
set "APP_DIR=%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$wsh = New-Object -ComObject WScript.Shell; " ^
    "$desktop = [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop); " ^
    "$shortcut = $wsh.CreateShortcut((Join-Path $desktop 'Restaurante 2026.lnk')); " ^
    "$shortcut.TargetPath = 'wscript.exe'; " ^
    "$shortcut.Arguments = '\"%VBS_SCRIPT%\"'; " ^
    "$shortcut.WorkingDirectory = '%APP_DIR%'; " ^
    "if (Test-Path -LiteralPath '%ICO_FILE%') { $shortcut.IconLocation = '%ICO_FILE%,0' }; " ^
    "$shortcut.Description = 'Sistema de Gestion de Restaurante 2026'; " ^
    "$shortcut.Save(); " ^
    "Write-Host '  [OK] Acceso directo Restaurante 2026.lnk creado en el Escritorio.' -ForegroundColor Green"

if errorlevel 1 (
    echo.
    echo [ERROR] No se pudo crear el acceso directo.
    pause
    exit /b 1
)

echo.
echo ========================================================
echo  ACCESO DIRECTO CREADO EXITOSAMENTE EN EL ESCRITORIO
echo  (El acceso directo no modifica la base de datos)
echo ========================================================
echo.
pause
exit /b 0
