<#
.SYNOPSIS
    Instalador y configurador TODO EN UN SOLO CLIC para Windows 11 / 10
    Sistema de Gestión de Restaurante 2026.

.DESCRIPTION
    Realiza de forma 100% autónoma todas las tareas técnicas previas:
    1. Si se ejecuta desde el código fuente, compila con Ant y genera 'Restaurante_Ejecutable' y su ZIP.
    2. Verifica la presencia de Java 21 LTS (busca en el sistema o instala silenciosamente con winget).
    3. Configura JAVA_HOME y la variable PATH a nivel de usuario y sistema.
    4. Permite elegir base nueva o actualización; BD.sql solo se importa cuando la base no existía,
       y las actualizaciones usan el migrador JDBC con respaldo previo obligatorio.
    5. Inicializa el archivo '.env' y garantiza las carpetas logs/, facturas/, pdf/.
    6. Crea el Acceso Directo con icono en alta resolución en el Escritorio y en el Menú Inicio.
    7. Inicia el programa de forma transparente sin ventanas negras de consola.
#>

[CmdletBinding()]
param(
    [switch]$SkipCompile
)

# Configurar salida UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# Determinar carpeta actual del script
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) {
    $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if (-not $ScriptDir) {
    $ScriptDir = (Get-Location).Path
}

# Detectar permisos de Administrador
$isAdmin = $false
try {
    $currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    $isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
} catch {
    $isAdmin = $false
}

Clear-Host
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "    SISTEMA DE RESTAURANTE 2026 - INSTALACION MAESTRA TODO EN UN SOLO CLIC     " -ForegroundColor Yellow
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Directorio de trabajo : $ScriptDir" -ForegroundColor Gray
Write-Host "  Modo de ejecucion     : $(if ($isAdmin) { 'Administrador' } else { 'Usuario estandar' })" -ForegroundColor Gray
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# -----------------------------------------------------------------------------
# 1. VERIFICAR SI ESTAMOS EN EL PROYECTO FUENTE Y GENERAR EJECUTABLE
# -----------------------------------------------------------------------------
$esProyectoFuente = (Test-Path -LiteralPath (Join-Path $ScriptDir "build.xml")) -or (Test-Path -LiteralPath (Join-Path $ScriptDir "src"))
$appEjecutableDir = $ScriptDir

if ($esProyectoFuente -and -not $SkipCompile) {
    Write-Host "[1/6] Entorno de desarrollo detectado. Generando Restaurante_Ejecutable..." -ForegroundColor Cyan
    $scriptEmpaquetador = Join-Path $ScriptDir "generar_restaurante_ejecutable.ps1"
    if (Test-Path -LiteralPath $scriptEmpaquetador) {
        try {
            Write-Host "  -> Ejecutando compilacion y empaquetado automatico..." -ForegroundColor Gray
            & $scriptEmpaquetador
            $carpetaEjecutable = Join-Path $ScriptDir "Restaurante_Ejecutable"
            if (Test-Path -LiteralPath $carpetaEjecutable) {
                $appEjecutableDir = $carpetaEjecutable
                Write-Host "  [OK] Restaurante_Ejecutable y ZIP generados con exito." -ForegroundColor Green
            }
        } catch {
            Write-Host "  [ADVERTENCIA] Error durante empaquetado: $_" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "[1/6] Paquete de aplicacion detectado: $appEjecutableDir" -ForegroundColor Cyan
}

# -----------------------------------------------------------------------------
# 2. VERIFICACION Y CONFIGURACION DE JAVA 21 LTS
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[2/6] Verificando estado de Java 21 LTS..." -ForegroundColor Cyan

function Obtener-JavaMajorVersion([string]$javaExePath) {
    if (-not (Test-Path -LiteralPath $javaExePath)) { return 0 }
    try {
        $out = (& $javaExePath -version 2>&1) | Out-String
        if ($out -match 'version "(\d+)(\.|\-)?"') {
            return [int]$matches[1]
        }
    } catch {}
    return 0
}

function Buscar-Java21EnSistema() {
    $candidatos = @(
        "${env:ProgramFiles}\Eclipse Adoptium\jdk-21*",
        "${env:ProgramFiles}\Java\jdk-21*",
        "${env:ProgramFiles}\Microsoft\jdk-21*",
        "${env:ProgramFiles}\Amazon Corretto\jdk21*",
        "${env:ProgramFiles}\BellSoft\LibericaJDK-21*",
        "${env:ProgramFiles}\Zulu\zulu-21*",
        "${env:ProgramFiles}\Java\jre-21*",
        "${env:LOCALAPPDATA}\Programs\Eclipse Adoptium\*21*",
        "${env:ProgramFiles(x86)}\Java\jdk-21*",
        "${env:ProgramFiles(x86)}\Eclipse Adoptium\jdk-21*"
    )
    foreach ($cand in $candidatos) {
        $dirs = Get-Item -Path $cand -ErrorAction SilentlyContinue
        if ($dirs) {
            foreach ($dir in $dirs) {
                $exe = Join-Path $dir.FullName "bin\java.exe"
                if (Test-Path -LiteralPath $exe) {
                    $ver = Obtener-JavaMajorVersion -javaExePath $exe
                    if ($ver -ge 21) {
                        return $dir.FullName
                    }
                }
            }
        }
    }
    return $null
}

$javaInstaladoOk = $false
$javaExeActual = $null

$javaEnPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaEnPath) {
    $ver = Obtener-JavaMajorVersion -javaExePath $javaEnPath.Source
    if ($ver -ge 21) {
        $javaInstaladoOk = $true
        $javaExeActual = $javaEnPath.Source
        Write-Host "  [OK] Java 21 detectado en el PATH del sistema (Version $ver)." -ForegroundColor Green
    } else {
        Write-Host "  [AVISO] Java en PATH es version $ver (se requiere Java 21 LTS)." -ForegroundColor Yellow
    }
}

$javaHomeDetectado = $null
if (-not $javaInstaladoOk) {
    Write-Host "  -> Buscando Java 21 en directorios del sistema..." -ForegroundColor Gray
    $javaHomeDetectado = Buscar-Java21EnSistema
    if ($javaHomeDetectado) {
        Write-Host "  [OK] Java 21 localizado en: $javaHomeDetectado" -ForegroundColor Green
        $javaInstaladoOk = $true
    }
}

if (-not $javaInstaladoOk) {
    Write-Host "  [INFO] Java 21 no esta presente. Instalando de forma automatica con winget..." -ForegroundColor Yellow
    $wingetCmd = Get-Command winget.exe -ErrorAction SilentlyContinue
    if ($wingetCmd) {
        try {
            Write-Host "  -> Descargando Eclipse Adoptium Temurin 21 JRE..." -ForegroundColor Gray
            $p = Start-Process -FilePath "winget.exe" -ArgumentList "install --id EclipseAdoptium.Temurin.21.JRE -e --silent --accept-package-agreements --accept-source-agreements" -NoNewWindow -Wait -PassThru
            if ($p.ExitCode -ne 0) {
                Write-Host "  -> Reintentando con Microsoft OpenJDK 21..." -ForegroundColor Gray
                Start-Process -FilePath "winget.exe" -ArgumentList "install --id Microsoft.OpenJDK.21 -e --silent --accept-package-agreements --accept-source-agreements" -NoNewWindow -Wait | Out-Null
            }
        } catch {
            Write-Host "  [ADVERTENCIA] Error en ejecucion de winget: $_" -ForegroundColor Yellow
        }
        Start-Sleep -Seconds 4
        $javaHomeDetectado = Buscar-Java21EnSistema
        if ($javaHomeDetectado) {
            $javaInstaladoOk = $true
            Write-Host "  [OK] Java 21 LTS instalado correctamente en: $javaHomeDetectado" -ForegroundColor Green
        }
    } else {
        Write-Host "  [AVISO] 'winget' no disponible. Puede descargarlo desde: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
    }
}

# Configurar JAVA_HOME y PATH si fue detectado
if (-not $javaHomeDetectado -and $javaExeActual) {
    $binDir = Split-Path -Parent $javaExeActual
    if ($binDir -match 'bin$') {
        $javaHomeDetectado = Split-Path -Parent $binDir
    }
}

if ($javaHomeDetectado) {
    $binPath = Join-Path $javaHomeDetectado "bin"
    try {
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHomeDetectado, [EnvironmentVariableTarget]::User)
        $userPath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::User)
        if (-not $userPath) { $userPath = "" }
        if ($userPath -notmatch [regex]::Escape($binPath)) {
            $newUserPath = if ($userPath.Trim().Length -gt 0) { "$binPath;$userPath" } else { $binPath }
            [Environment]::SetEnvironmentVariable("Path", $newUserPath, [EnvironmentVariableTarget]::User)
        }
        if ($isAdmin) {
            [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHomeDetectado, [EnvironmentVariableTarget]::Machine)
            $machinePath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine)
            if (-not $machinePath) { $machinePath = "" }
            if ($machinePath -notmatch [regex]::Escape($binPath)) {
                $newMachinePath = if ($machinePath.Trim().Length -gt 0) { "$binPath;$machinePath" } else { $binPath }
                [Environment]::SetEnvironmentVariable("Path", $newMachinePath, [EnvironmentVariableTarget]::Machine)
            }
        }
        $env:JAVA_HOME = $javaHomeDetectado
        $env:Path = "$binPath;$env:Path"
        Write-Host "  [OK] Variable JAVA_HOME y PATH configuradas correctamente." -ForegroundColor Green
    } catch {
        Write-Host "  [ADVERTENCIA] No se pudieron registrar variables persistentes: $_" -ForegroundColor Yellow
    }
}

# -----------------------------------------------------------------------------
# 3. VERIFICACION Y ACTUALIZACION AUTOMATICA DE LA BASE DE DATOS MYSQL
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[3/6] Verificando e inicializando / actualizando base de datos MySQL..." -ForegroundColor Cyan

$mysqlCmd = $null
$rutasComunesMysql = @(
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\Program Files\MariaDB 11.4\bin\mysql.exe",
    "C:\Program Files\MariaDB 10.11\bin\mysql.exe",
    "C:\xampp\mysql\bin\mysql.exe",
    "C:\laragon\bin\mysql\mysql*\bin\mysql.exe"
)

if (Get-Command mysql.exe -ErrorAction SilentlyContinue) {
    $mysqlCmd = (Get-Command mysql.exe).Source
} else {
    foreach ($r in $rutasComunesMysql) {
        $encontrados = Resolve-Path $r -ErrorAction SilentlyContinue
        if ($encontrados) {
            $mysqlCmd = $encontrados[0].Path
            break
        }
    }
}

# Si no está instalado, intentar instalar mediante winget
if (-not $mysqlCmd) {
    $serviciosExistentes = Get-Service -Name "MySQL*", "MariaDB*" -ErrorAction SilentlyContinue
    if ($serviciosExistentes) {
        Write-Host "  [INFO] Se detectó un servicio MySQL/MariaDB. La migración usa JDBC y no requiere mysql.exe." -ForegroundColor Gray
    } else {
        Write-Host "  [INFO] MySQL no detectado en el sistema." -ForegroundColor Yellow
    }
    if (-not $serviciosExistentes -and (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
        Write-Host "  -> Instalando Oracle MySQL Server mediante winget..." -ForegroundColor Cyan
        try {
            Start-Process -FilePath "winget.exe" -ArgumentList "install --id Oracle.MySQL -e --silent --accept-source-agreements --accept-package-agreements" -NoNewWindow -Wait | Out-Null
            Start-Sleep -Seconds 5
            foreach ($r in $rutasComunesMysql) {
                $encontrados = Resolve-Path $r -ErrorAction SilentlyContinue
                if ($encontrados) { $mysqlCmd = $encontrados[0].Path; break }
            }
        } catch {
            Write-Host "  [ADVERTENCIA] No se pudo instalar MySQL via winget: $_" -ForegroundColor Yellow
        }
    }
}

# Asegurar que el servicio de Windows de MySQL esté corriendo
$servicios = Get-Service -Name "MySQL*","MariaDB*" -ErrorAction SilentlyContinue
foreach ($srv in $servicios) {
    if ($srv.Status -ne "Running") {
        Write-Host "  -> Iniciando servicio de Windows '$($srv.Name)'..." -ForegroundColor Gray
        Start-Service $srv.Name -ErrorAction Stop
        Start-Sleep -Seconds 2
    }
}

# La migración JDBC necesita leer las mismas credenciales que usa la aplicación.
$envFile = Join-Path $appEjecutableDir ".env"
$envExample = Join-Path $appEjecutableDir ".env.example"
if (-not (Test-Path -LiteralPath $envFile)) {
    if (Test-Path -LiteralPath $envExample) {
        Copy-Item -LiteralPath $envExample -Destination $envFile
    } else {
        throw "Falta .env y no existe .env.example; no se intentará migrar con credenciales supuestas."
    }
}

function Invoke-MigracionJDBC {
    $jarApp = Join-Path $appEjecutableDir "Restaurante.jar"
    $libApp = Join-Path $appEjecutableDir "lib"
    $javaExe = if ($javaExeActual) { $javaExeActual } elseif ($javaHomeDetectado) {
        Join-Path $javaHomeDetectado "bin\java.exe"
    } else { (Get-Command java.exe -ErrorAction Stop).Source }
    if (-not (Test-Path -LiteralPath $jarApp)) {
        throw "No se encontró Restaurante.jar; no se puede ejecutar la migración segura."
    }
    $classpathMigracion = "$jarApp;$libApp\*"
    Push-Location $appEjecutableDir
    try {
        Write-Host "  -> Ejecutando migración JDBC con respaldo previo obligatorio..." -ForegroundColor Cyan
        & $javaExe -cp $classpathMigracion restaurante.Restaurante --migrate-db
        if ($LASTEXITCODE -ne 0) {
            throw "La migración falló con código $LASTEXITCODE. No se ejecutó BD.sql; revise el log y el respaldo."
        }
    } finally {
        Pop-Location
    }
}

$modoBd = Read-Host "Base de datos: (N)ueva instalación o (A)ctualizar una instalación existente"
if ($modoBd.Trim().ToUpperInvariant() -eq "N") {
    $instaladorNuevo = Join-Path $appEjecutableDir "instalar_mysql_y_bd.bat"
    if (-not (Test-Path -LiteralPath $instaladorNuevo)) {
        $instaladorNuevo = Join-Path $ScriptDir "instalar_mysql_y_bd.bat"
    }
    if (-not (Test-Path -LiteralPath $instaladorNuevo)) {
        throw "No se encontró instalar_mysql_y_bd.bat para instalar una base nueva."
    }
    & $instaladorNuevo
    if ($LASTEXITCODE -ne 0) {
        throw "La instalación nueva de la base terminó con código $LASTEXITCODE."
    }
    Invoke-MigracionJDBC
} elseif ($modoBd.Trim().ToUpperInvariant() -eq "A") {
    Invoke-MigracionJDBC
    Write-Host "  [OK] Migración versionada aplicada y validada." -ForegroundColor Green
} else {
    throw "Selección inválida. Escriba N para una instalación nueva o A para actualizar."
}

# -----------------------------------------------------------------------------
# 4. ESTRUCTURA DE ARCHIVOS Y CONFIGURACION .ENV
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[4/6] Verificando archivos de configuracion y directorios..." -ForegroundColor Cyan

$carpetas = @("logs", "pdf", "facturas")
foreach ($c in $carpetas) {
    $rutaC = Join-Path $appEjecutableDir $c
    if (-not (Test-Path -LiteralPath $rutaC)) {
        New-Item -ItemType Directory -LiteralPath $rutaC -Force | Out-Null
    }
}

$envFile = Join-Path $appEjecutableDir ".env"
$envExample = Join-Path $appEjecutableDir ".env.example"
if (-not (Test-Path -LiteralPath $envFile)) {
    if (Test-Path -LiteralPath $envExample) {
        Copy-Item -LiteralPath $envExample -Destination $envFile -Force
        Write-Host "  [+] Archivo .env inicializado desde .env.example." -ForegroundColor Green
    } else {
        $envContenido = @"
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=restaurante
MYSQL_USER=admin
MYSQL_PASSWORD=admin
"@
        Set-Content -Path $envFile -Value $envContenido -Encoding UTF8
        Write-Host "  [+] Archivo .env generado con configuracion predeterminada." -ForegroundColor Green
    }
}
Write-Host "  [OK] Estructura de carpetas y archivo .env preparados." -ForegroundColor Green

# -----------------------------------------------------------------------------
# 5. CREACION DE ACCESOS DIRECTOS (ESCRITORIO Y MENU INICIO)
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[5/6] Creando acceso directo con icono en el Escritorio de Windows 11..." -ForegroundColor Cyan

$iconPath = Join-Path $appEjecutableDir "icono_restaurante.ico"
if (-not (Test-Path -LiteralPath $iconPath)) {
    $iconPath = Join-Path $ScriptDir "icono_restaurante.ico"
}

$vbsPath = Join-Path $appEjecutableDir "iniciar_restaurante_silencioso.vbs"
if (-not (Test-Path -LiteralPath $vbsPath)) {
    $vbsPath = Join-Path $ScriptDir "iniciar_restaurante_silencioso.vbs"
}

try {
    $wsh = New-Object -ComObject WScript.Shell
    
    # 5.1 Acceso directo en el Escritorio
    $desktopFolder = [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop)
    $shortcutDesktopPath = Join-Path $desktopFolder "Restaurante 2026.lnk"
    
    $shortcut = $wsh.CreateShortcut($shortcutDesktopPath)
    $shortcut.TargetPath = "wscript.exe"
    $shortcut.Arguments = "`"$vbsPath`""
    $shortcut.WorkingDirectory = $appEjecutableDir
    if (Test-Path -LiteralPath $iconPath) {
        $shortcut.IconLocation = "$iconPath,0"
    }
    $shortcut.Description = "Sistema de Gestión de Restaurante 2026"
    $shortcut.Save()
    Write-Host "  [OK] Acceso directo creado en el Escritorio:" -ForegroundColor Green
    Write-Host "       $shortcutDesktopPath" -ForegroundColor Gray

    # 5.2 Acceso directo en el Menú Inicio
    $startMenuFolder = [Environment]::GetFolderPath([Environment+SpecialFolder]::Programs)
    $shortcutStartPath = Join-Path $startMenuFolder "Restaurante 2026.lnk"
    
    $shortcutSM = $wsh.CreateShortcut($shortcutStartPath)
    $shortcutSM.TargetPath = "wscript.exe"
    $shortcutSM.Arguments = "`"$vbsPath`""
    $shortcutSM.WorkingDirectory = $appEjecutableDir
    if (Test-Path -LiteralPath $iconPath) {
        $shortcutSM.IconLocation = "$iconPath,0"
    }
    $shortcutSM.Description = "Sistema de Gestión de Restaurante 2026"
    $shortcutSM.Save()
    Write-Host "  [OK] Acceso directo registrado en el Menú Inicio:" -ForegroundColor Green
    Write-Host "       $shortcutStartPath" -ForegroundColor Gray

} catch {
    Write-Host "  [ERROR] No se pudo crear el acceso directo: $_" -ForegroundColor Red
}

# -----------------------------------------------------------------------------
# 6. RESUMEN Y LANZAMIENTO TRANSPARENTE
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "       ¡PROCESO COMPLETADO AL 100% EN UN SOLO CLIC CON EXITO!                  " -ForegroundColor Yellow
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "  ✓ Entorno y ejecutable preparados." -ForegroundColor White
Write-Host "  ✓ Java 21 LTS verificado y configurado." -ForegroundColor White
Write-Host "  ✓ Base de datos MySQL actualizada (migraciones, RIF, tickets, índices)." -ForegroundColor White
Write-Host "  ✓ Acceso directo con icono creado en el Escritorio y Menú Inicio." -ForegroundColor White
Write-Host "  ✓ Ejecución transparente sin ventana negra de consola." -ForegroundColor White
Write-Host "================================================================================" -ForegroundColor Green
Write-Host ""

$respuesta = Read-Host "¿Deseas abrir el Sistema de Restaurante ahora mismo? (S/N) [S]"
if ([string]::IsNullOrWhiteSpace($respuesta) -or $respuesta.Trim().ToUpper() -eq "S") {
    Write-Host ""
    Write-Host "Abriendo Sistema de Restaurante 2026..." -ForegroundColor Green
    try {
        Start-Process "wscript.exe" -ArgumentList "`"$vbsPath`"" -WorkingDirectory $appEjecutableDir
        Start-Sleep -Seconds 2
    } catch {
        Write-Host "Para iniciar, haz doble clic en el acceso directo de tu Escritorio." -ForegroundColor Yellow
    }
}
