# ========================================================
# Script PowerShell para Configuración Automática de MySQL
# Sistema Restaurante 2026
# ========================================================

# Auto-elevar si no tiene privilegios de administrador
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "[INFO] Elevando permisos a Administrador..." -ForegroundColor Yellow
    Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  CONFIGURADOR AUTOMATICO DE MYSQL - RESTAURANTE 2026   " -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $PSCommandPath
if (-not $ScriptDir) { $ScriptDir = Get-Location }

$sqlFile = Join-Path $ScriptDir "BD.sql"
if (-not (Test-Path $sqlFile)) {
    Write-Host "[ERROR] No se encontro el archivo BD.sql en: $ScriptDir" -ForegroundColor Red
    pause
    exit 1
}

# 1. Localizar mysql.exe
$mysqlCmd = $null
$rutasComunes = @(
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\Program Files\MariaDB 11.4\bin\mysql.exe",
    "C:\Program Files\MariaDB 10.11\bin\mysql.exe",
    "C:\xampp\mysql\bin\mysql.exe",
    "C:\laragon\bin\mysql\mysql*\bin\mysql.exe"
)

if (Get-Command mysql -ErrorAction SilentlyContinue) {
    $mysqlCmd = (Get-Command mysql).Source
} else {
    foreach ($ruta in $rutasComunes) {
        $encontrados = Resolve-Path $ruta -ErrorAction SilentlyContinue
        if ($encontrados) {
            $mysqlCmd = $encontrados[0].Path
            break
        }
    }
}

# 2. Si no esta instalado, intentar con winget
if (-not $mysqlCmd) {
    Write-Host "[INFO] MySQL/MariaDB no fue detectado en el sistema." -ForegroundColor Yellow
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "[INFO] Instalando MySQL Server via winget (Windows Package Manager)..." -ForegroundColor Cyan
        winget install --id Oracle.MySQL -e --accept-source-agreements --accept-package-agreements
        Start-Sleep -Seconds 5
        foreach ($ruta in $rutasComunes) {
            $encontrados = Resolve-Path $ruta -ErrorAction SilentlyContinue
            if ($encontrados) { $mysqlCmd = $encontrados[0].Path; break }
        }
    } else {
        Write-Host "[AVISO] winget no esta disponible." -ForegroundColor Yellow
    }
}

if (-not $mysqlCmd) {
    Write-Host "[ERROR] No se pudo encontrar 'mysql.exe'." -ForegroundColor Red
    Write-Host "Por favor instale MySQL o MariaDB y vuelva a ejecutar este script." -ForegroundColor Yellow
    pause
    exit 1
}

Write-Host "[OK] Cliente MySQL localizado en: $mysqlCmd" -ForegroundColor Green

# 3. Asegurar que el servicio de Windows este corriendo
$servicios = Get-Service -Name "MySQL*","MariaDB*" -ErrorAction SilentlyContinue
foreach ($srv in $servicios) {
    if ($srv.Status -ne "Running") {
        Write-Host "Iniciando servicio de Windows $($srv.Name)..." -ForegroundColor Cyan
        Start-Service $srv.Name -ErrorAction SilentlyContinue
    }
}

# 4. Solicitar credencial root
Write-Host ""
Write-Host "Ingrese la contrasena de ROOT de MySQL (o presione ENTER si esta vacia):" -ForegroundColor Yellow
$rootPass = Read-Host -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPass)
$plainRootPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
$passParam = if ($plainRootPass) { "-p$plainRootPass" } else { "" }

# 5. Crear Base de Datos
Write-Host "[1/3] Creando base de datos restaurante..." -ForegroundColor Cyan
$cmdCreateDB = "CREATE DATABASE IF NOT EXISTS restaurante CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ($passParam) {
    & $mysqlCmd -u root $passParam -e $cmdCreateDB
} else {
    & $mysqlCmd -u root -e $cmdCreateDB
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] No se pudo conectar como root a MySQL. Verifique la contrasena." -ForegroundColor Red
    pause
    exit 1
}

# 6. Crear usuario admin / admin
Write-Host "[2/3] Configurando usuario 'admin' con clave 'admin'..." -ForegroundColor Cyan
$cmdUser = @"
CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY 'admin';
CREATE USER IF NOT EXISTS 'admin'@'127.0.0.1' IDENTIFIED BY 'admin';
CREATE USER IF NOT EXISTS 'admin'@'%' IDENTIFIED BY 'admin';
ALTER USER 'admin'@'localhost' IDENTIFIED BY 'admin';
ALTER USER 'admin'@'127.0.0.1' IDENTIFIED BY 'admin';
ALTER USER 'admin'@'%' IDENTIFIED BY 'admin';
GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'localhost';
GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'127.0.0.1';
GRANT ALL PRIVILEGES ON restaurante.* TO 'admin'@'%';
FLUSH PRIVILEGES;
"@

if ($passParam) {
    & $mysqlCmd -u root $passParam -e $cmdUser
} else {
    & $mysqlCmd -u root -e $cmdUser
}

# 7. Importar BD.sql
Write-Host "[3/3] Importando tablas y datos desde BD.sql..." -ForegroundColor Cyan
$contentSql = Get-Content -Raw -Encoding UTF8 $sqlFile
$contentSql | & $mysqlCmd -u admin -padmin restaurante

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host " BASE DE DATOS Y USUARIOS CONFIGURADOS CON EXITO!" -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host "Base de datos : restaurante" -ForegroundColor White
    Write-Host "Usuario MySQL : admin" -ForegroundColor White
    Write-Host "Clave MySQL   : admin" -ForegroundColor White
    Write-Host ""
    Write-Host "Ya puede iniciar el sistema ejecutando: iniciar_restaurante.bat" -ForegroundColor Yellow
} else {
    Write-Host "[ADVERTENCIA] La importacion finalizo con codigo: $LASTEXITCODE" -ForegroundColor Yellow
}

Write-Host ""
pause
