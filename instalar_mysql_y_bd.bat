@echo off
setlocal EnableDelayedExpansion
title Instalador y Configurador de MySQL para Restaurante 2026
cd /d "%~dp0"

:: 1. Auto-elevar a Administrador si no tiene permisos
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Solicitando permisos de Administrador...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

:: 2. Ejecutar script PowerShell embebido
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path;" ^
    "if (-not $ScriptDir) { $ScriptDir = Get-Location };" ^
    "Write-Host '========================================================' -ForegroundColor Cyan;" ^
    "Write-Host '   INSTALADOR Y CONFIGURADOR DE MYSQL - RESTAURANTE 2026' -ForegroundColor Yellow;" ^
    "Write-Host '========================================================' -ForegroundColor Cyan;" ^
    "Write-Host '';" ^
    "$sqlFile = Join-Path $ScriptDir 'BD.sql';" ^
    "if (-not (Test-Path $sqlFile)) {" ^
    "    Write-Host '[ERROR] No se encontro el archivo BD.sql en la carpeta actual.' -ForegroundColor Red;" ^
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
    "    Write-Host '[INFO] MySQL/MariaDB no fue detectado en el sistema.' -ForegroundColor Yellow;" ^
    "    Write-Host '[INFO] Intentando instalar via Windows Package Manager (winget)...' -ForegroundColor Cyan;" ^
    "    if (Get-Command winget -ErrorAction SilentlyContinue) {" ^
    "        Write-Host 'Instalando MySQL Server... Por favor espere.' -ForegroundColor Green;" ^
    "        winget install --id Oracle.MySQL -e --accept-source-agreements --accept-package-agreements;" ^
    "        Start-Sleep -Seconds 5;" ^
    "        foreach ($ruta in $rutasComunes) {" ^
    "            $encontrados = Resolve-Path $ruta -ErrorAction SilentlyContinue;" ^
    "            if ($encontrados) { $mysqlCmd = $encontrados[0].Path; break; }" ^
    "        }" ^
    "    } else {" ^
    "        Write-Host '[AVISO] winget no esta disponible en este equipo.' -ForegroundColor Yellow;" ^
    "    }" ^
    "};" ^
    "if (-not $mysqlCmd) {" ^
    "    Write-Host '[ERROR] No se pudo localizar mysql.exe despues de la verificacion.' -ForegroundColor Red;" ^
    "    Write-Host 'Por favor instale MySQL o MariaDB y vuelva a ejecutar este archivo.' -ForegroundColor Yellow;" ^
    "    pause; exit 1;" ^
    "};" ^
    "Write-Host ('[OK] Cliente MySQL encontrado en: ' + $mysqlCmd) -ForegroundColor Green;" ^
    "$servicios = Get-Service -Name 'MySQL*','MariaDB*' -ErrorAction SilentlyContinue;" ^
    "foreach ($srv in $servicios) {" ^
    "    if ($srv.Status -ne 'Running') {" ^
    "        Write-Host ('Iniciando servicio ' + $srv.Name + '...') -ForegroundColor Cyan;" ^
    "        Start-Service $srv.Name -ErrorAction SilentlyContinue;" ^
    "    }" ^
    "};" ^
    "Write-Host '';" ^
    "Write-Host 'Ingrese la contrasena de ROOT de su MySQL (presione ENTER si no tiene contrasena):' -ForegroundColor Yellow;" ^
    "$rootPass = Read-Host -AsSecureString;" ^
    "$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPass);" ^
    "$plainRootPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr);" ^
    "$passParam = if ($plainRootPass) { '-p' + $plainRootPass } else { '' };" ^
    "Write-Host '[1/3] Creando base de datos restaurante...' -ForegroundColor Cyan;" ^
    "$cmdCreateDB = 'CREATE DATABASE IF NOT EXISTS restaurante CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;';" ^
    "& $mysqlCmd -u root $passParam -e $cmdCreateDB;" ^
    "if ($LASTEXITCODE -ne 0) {" ^
    "    Write-Host '[ERROR] No se pudo conectar como root a MySQL. Verifique la contrasena ingresada.' -ForegroundColor Red;" ^
    "    pause; exit 1;" ^
    "};" ^
    "Write-Host '[2/3] Configurando usuario admin y permisos...' -ForegroundColor Cyan;" ^
    "$cmdUser = @(" ^
    "    \"CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY 'admin';\"," ^
    "    \"CREATE USER IF NOT EXISTS 'admin'@'127.0.0.1' IDENTIFIED BY 'admin';\"," ^
    "    \"CREATE USER IF NOT EXISTS 'admin'@'%' IDENTIFIED BY 'admin';\"," ^
    "    \"ALTER USER 'admin'@'localhost' IDENTIFIED BY 'admin';\"," ^
    "    \"ALTER USER 'admin'@'127.0.0.1' IDENTIFIED BY 'admin';\"," ^
    "    \"GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'localhost';\"," ^
    "    \"GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'127.0.0.1';\"," ^
    "    \"GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'%';\"," ^
    "    \"FLUSH PRIVILEGES;\"" ^
    ") -join ' ';" ^
    "& $mysqlCmd -u root $passParam -e $cmdUser;" ^
    "Write-Host '[3/3] Importando esquema y datos desde BD.sql...' -ForegroundColor Cyan;" ^
    "$cmdImport = \"USE restaurante; SOURCE $sqlFile;\";" ^
    "$contentSql = Get-Content -Raw -Encoding UTF8 $sqlFile;" ^
    "$contentSql | & $mysqlCmd -u admin -padmin restaurante;" ^
    "if ($LASTEXITCODE -eq 0) {" ^
    "    Write-Host '';" ^
    "    Write-Host '========================================================' -ForegroundColor Green;" ^
    "    Write-Host ' BASE DE DATOS Y USUARIOS CONFIGURADOS CON EXITO!' -ForegroundColor Green;" ^
    "    Write-Host '========================================================' -ForegroundColor Green;" ^
    "    Write-Host 'Base de datos : restaurante' -ForegroundColor White;" ^
    "    Write-Host 'Usuario MySQL : admin' -ForegroundColor White;" ^
    "    Write-Host 'Clave MySQL   : admin' -ForegroundColor White;" ^
    "    Write-Host '';" ^
    "    Write-Host 'Ya puede ejecutar iniciar_restaurante.bat' -ForegroundColor Yellow;" ^
    "} else {" ^
    "    Write-Host '[ADVERTENCIA] La importacion devolvio codigo: ' $LASTEXITCODE -ForegroundColor Yellow;" ^
    "};"

echo.
pause
