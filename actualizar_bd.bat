@echo off
setlocal EnableDelayedExpansion
title Actualizador de Base de Datos - Restaurante 2026
cd /d "%~dp0"

echo ========================================================
echo   ACTUALIZADOR DE BASE DE DATOS - RESTAURANTE 2026
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path;" ^
    "if (-not $ScriptDir) { $ScriptDir = Get-Location };" ^
    "$sqlFile = Join-Path $ScriptDir 'actualizar_bd.sql';" ^
    "if (-not (Test-Path $sqlFile)) {" ^
    "    Write-Host '[ERROR] No se encontro el archivo actualizar_bd.sql en la carpeta.' -ForegroundColor Red;" ^
    "    pause; exit 1;" ^
    "};" ^
    "$mysqlCmd = $null;" ^
    "$rutasComunes = @(" ^
    "    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'," ^
    "    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'," ^
    "    'C:\Program Files\MariaDB 11.4\bin\mysql.exe'," ^
    "    'C:\Program Files\MariaDB 10.11\bin\mysql.exe'," ^
    "    'C:\xampp\mysql\bin\mysql.exe'," ^
    "    'C:\laragon\bin\mysql\mysql*\bin\mysql.exe'" ^
    ");" ^
    "if (Get-Command mysql -ErrorAction SilentlyContinue) {" ^
    "    $mysqlCmd = (Get-Command mysql).Source;" ^
    "} else {" ^
    "    foreach ($ruta in $rutasComunes) {" ^
    "        $encontrados = Resolve-Path $ruta -ErrorAction SilentlyContinue;" ^
    "        if ($encontrados) { $mysqlCmd = $encontrados[0].Path; break; }" ^
    "    }" ^
    "};" ^
    "if (-not $mysqlCmd) {" ^
    "    Write-Host '[ERROR] No se pudo localizar mysql.exe. Verifique que MySQL este instalado.' -ForegroundColor Red;" ^
    "    pause; exit 1;" ^
    "};" ^
    "Write-Host ('[OK] Cliente MySQL: ' + $mysqlCmd) -ForegroundColor Green;" ^
    "Write-Host 'Aplicando migraciones a la base de datos restaurante...' -ForegroundColor Cyan;" ^
    "$contentSql = Get-Content -Raw -Encoding UTF8 $sqlFile;" ^
    "$contentSql | & $mysqlCmd -u admin -padmin restaurante;" ^
    "if ($LASTEXITCODE -ne 0) {" ^
    "    Write-Host '[INFO] Reintentando con usuario root de MySQL. Ingrese la clave (o Enter si no tiene):' -ForegroundColor Yellow;" ^
    "    $rootPass = Read-Host -AsSecureString;" ^
    "    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPass);" ^
    "    $plainRootPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr);" ^
    "    $passParam = if ($plainRootPass) { '-p' + $plainRootPass } else { '' };" ^
    "    $contentSql | & $mysqlCmd -u root $passParam restaurante;" ^
    "};" ^
    "if ($LASTEXITCODE -eq 0) {" ^
    "    Write-Host '';" ^
    "    Write-Host '========================================================' -ForegroundColor Green;" ^
    "    Write-Host ' BASE DE DATOS ACTUALIZADA CON EXITO (SIN PERDIDA DE DATOS)' -ForegroundColor Green;" ^
    "    Write-Host '========================================================' -ForegroundColor Green;" ^
    "    Write-Host 'Se actualizaron: RIF, logo, campos de IVA, tasas, clientes, metodo de pago e indices.' -ForegroundColor White;" ^
    "} else {" ^
    "    Write-Host '[ERROR] Hubo un problema al aplicar la actualizacion (Codigo: ' $LASTEXITCODE ')' -ForegroundColor Red;" ^
    "};"

echo.
pause
