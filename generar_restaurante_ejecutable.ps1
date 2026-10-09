<#
.SYNOPSIS
    Script de PowerShell para Windows 11 que borra, regenera y empaqueta la carpeta
    'Restaurante_Ejecutable' y crea su archivo ZIP 'Restaurante_Ejecutable.zip'.

.DESCRIPTION
    Diseñado para desarrolladores y socios que modifican el proyecto con Apache NetBeans y JDK 21.
    1. Verifica el entorno Java 21 y busca Apache Ant (o NetBeans Ant) para recompilar si es necesario.
    2. Elimina cualquier versión previa de 'Restaurante_Ejecutable' y 'Restaurante_Ejecutable.zip'.
    3. Regenera la estructura limpia y 100% funcional:
       - Restaurante.jar (compilado con Java 21)
       - lib/ (Driver MySQL, iText PDF, AbsoluteLayout)
       - .env (Configuración local de BD)
       - BD.sql y actualizar_bd.sql (Últimas migraciones e índices optimizados)
       - Scripts de lanzamiento (.bat, .sh) y utilidades de base de datos
       - Carpetas logs/, pdf/, facturas/
    4. Valida la integridad física de los archivos generados.
    5. Comprime la carpeta en 'Restaurante_Ejecutable.zip' listo para distribución.

.PARAMETER SkipCompile
    Omite el paso de compilación con Ant si ya se ejecutó 'Clean and Build' dentro de NetBeans.

.EXAMPLE
    .\generar_restaurante_ejecutable.ps1
    Recompila y regenera Restaurante_Ejecutable y Restaurante_Ejecutable.zip.

.EXAMPLE
    .\generar_restaurante_ejecutable.ps1 -SkipCompile
    Regenera y empaqueta usando el dist/Restaurante.jar existente.
#>

[CmdletBinding()]
param(
    [switch]$SkipCompile
)

# Configurar codificación UTF-8 en consola
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# Determinar directorio raíz del proyecto
$ProjectRoot = $PSScriptRoot
if (-not $ProjectRoot) {
    $ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if (-not $ProjectRoot) {
    $ProjectRoot = (Get-Location).Path
}

Set-Location -LiteralPath $ProjectRoot

$OutputDir = Join-Path $ProjectRoot "Restaurante_Ejecutable"
$ZipFile   = Join-Path $ProjectRoot "Restaurante_Ejecutable.zip"
$DistJar   = Join-Path $ProjectRoot "dist\Restaurante.jar"
$LibDir    = Join-Path $ProjectRoot "librerias"

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  REGENERADOR Y EMPAQUETADOR DE RESTAURANTE_EJECUTABLE (WINDOWS 11 / JDK 21)   " -ForegroundColor Yellow
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Directorio del proyecto : $ProjectRoot" -ForegroundColor Gray
Write-Host "  Destino ejecutable      : $OutputDir" -ForegroundColor Gray
Write-Host "  Archivo ZIP final       : $ZipFile" -ForegroundColor Gray
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# -----------------------------------------------------------------------------
# 1. VERIFICAR ENTORNO JAVA 21
# -----------------------------------------------------------------------------
Write-Host "[1/6] Verificando entorno Java 21..." -ForegroundColor Cyan

$javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCmd -and $env:JAVA_HOME) {
    $candidatoJava = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $candidatoJava) {
        $javaCmd = $candidatoJava
    }
}

if ($javaCmd) {
    try {
        $javaVersionOutput = (& java -version 2>&1) | Out-String
        if ($javaVersionOutput -match 'version "(\d+)(\.|\-)?"') {
            $majorVersion = [int]$matches[1]
            if ($majorVersion -ge 21) {
                Write-Host "  [OK] Java detectado: Version $majorVersion LTS (Compatible)" -ForegroundColor Green
            } else {
                Write-Host "  [ADVERTENCIA] Java detectado: Version $majorVersion. Se requiere JDK 21 LTS." -ForegroundColor Yellow
            }
        } else {
            Write-Host "  [INFO] Java disponible en el sistema." -ForegroundColor Gray
        }
    } catch {
        Write-Host "  [ADVERTENCIA] No se pudo consultar la versión de Java: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [ADVERTENCIA] No se detecto 'java.exe' en el PATH ni JAVA_HOME." -ForegroundColor Yellow
    Write-Host "                Asegurese de tener instalado JDK 21 para ejecutar la aplicacion." -ForegroundColor Yellow
}

# -----------------------------------------------------------------------------
# 2. COMPILACIÓN / GENERACIÓN DE dist\Restaurante.jar
# -----------------------------------------------------------------------------
Write-Host "`n[2/6] Verificando compilacion del proyecto..." -ForegroundColor Cyan

function Find-AntExecutable {
    # 1. Ant en PATH
    $cmd = Get-Command ant.bat -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command ant.cmd -ErrorAction SilentlyContinue }
    if (-not $cmd) { $cmd = Get-Command ant -ErrorAction SilentlyContinue }
    if ($cmd) { return $cmd.Source }

    # 2. Ant integrado en Apache NetBeans (versiones 12 a 35+)
    $candidatosNetBeans = @(
        "C:\Program Files\NetBeans*\extide\ant\bin\ant.bat",
        "C:\Program Files\Apache NetBeans*\extide\ant\bin\ant.bat",
        "$env:ProgramFiles\NetBeans*\extide\ant\bin\ant.bat",
        "$env:ProgramFiles\Apache NetBeans*\extide\ant\bin\ant.bat",
        "$env:LOCALAPPDATA\Programs\NetBeans*\extide\ant\bin\ant.bat",
        "$env:LOCALAPPDATA\Programs\Apache NetBeans*\extide\ant\bin\ant.bat",
        "$env:ProgramFiles(x86)\NetBeans*\extide\ant\bin\ant.bat",
        "$env:ProgramFiles(x86)\Apache NetBeans*\extide\ant\bin\ant.bat"
    )
    foreach ($patron in $candidatosNetBeans) {
        $hallados = Resolve-Path -Path $patron -ErrorAction SilentlyContinue
        if ($hallados) {
            return $hallados[-1].Path
        }
    }

    # 3. ANT_HOME
    if ($env:ANT_HOME -and (Test-Path "$env:ANT_HOME\bin\ant.bat")) {
        return "$env:ANT_HOME\bin\ant.bat"
    }

    return $null
}

$antExe = Find-AntExecutable

if (-not $SkipCompile -and $antExe) {
    Write-Host "  [INFO] Compilando con Apache Ant ($antExe)..." -ForegroundColor Gray
    & $antExe clean jar
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [ERROR] La compilacion con Ant finalizo con codigo $LASTEXITCODE." -ForegroundColor Red
        if (-not (Test-Path -LiteralPath $DistJar)) {
            Write-Host "  [ABORTADO] No existe '$DistJar'. Corrija los errores o compile desde NetBeans." -ForegroundColor Red
            exit 1
        }
        Write-Host "  [AVISO] Continuando con dist\Restaurante.jar previo..." -ForegroundColor Yellow
    } else {
        Write-Host "  [OK] dist\Restaurante.jar compilado y empaquetado exitosamente." -ForegroundColor Green
    }
} else {
    if (-not (Test-Path -LiteralPath $DistJar)) {
        Write-Host "  [ERROR] No se encontro '$DistJar' ni la herramienta Ant." -ForegroundColor Red
        Write-Host "  [ACCION REQUERIDA] Abra NetBeans y presione Shift+F11 (Clean and Build Project)," -ForegroundColor Yellow
        Write-Host "                    luego vuelva a ejecutar este script." -ForegroundColor Yellow
        exit 1
    } else {
        $fechaJar = (Get-Item -LiteralPath $DistJar).LastWriteTime
        Write-Host "  [OK] Usando dist\Restaurante.jar existente (Fecha: $fechaJar)." -ForegroundColor Green
    }
}

# -----------------------------------------------------------------------------
# 3. BORRAR Y REGENERAR CARPETA Restaurante_Ejecutable Y ZIP PREVIO
# -----------------------------------------------------------------------------
Write-Host "`n[3/6] Limpiando carpetas y artefactos previos..." -ForegroundColor Cyan

if (Test-Path -LiteralPath $OutputDir) {
    Write-Host "  -> Eliminando carpeta anterior '$OutputDir'..." -ForegroundColor Yellow
    Remove-Item -LiteralPath $OutputDir -Recurse -Force -ErrorAction Stop
    Start-Sleep -Milliseconds 200
}

if (Test-Path -LiteralPath $ZipFile) {
    Write-Host "  -> Eliminando archivo ZIP anterior '$ZipFile'..." -ForegroundColor Yellow
    Remove-Item -LiteralPath $ZipFile -Force -ErrorAction Stop
}

# -----------------------------------------------------------------------------
# 4. CREAR ESTRUCTURA LIMPIA Y COPIAR ARCHIVOS
# -----------------------------------------------------------------------------
Write-Host "`n[4/6] Regenerando estructura de Restaurante_Ejecutable..." -ForegroundColor Cyan

$carpetasRequeridas = @(
    $OutputDir,
    (Join-Path $OutputDir "lib"),
    (Join-Path $OutputDir "logs"),
    (Join-Path $OutputDir "pdf"),
    (Join-Path $OutputDir "facturas")
)

foreach ($carpeta in $carpetasRequeridas) {
    if (-not (Test-Path -LiteralPath $carpeta)) {
        New-Item -ItemType Directory -LiteralPath $carpeta -Force | Out-Null
    }
}
Write-Host "  [OK] Subdirectorios lib/, logs/, pdf/, facturas/ creados." -ForegroundColor Green

# 4.1 Copiar JAR principal
Write-Host "  -> Copiando Restaurante.jar..." -ForegroundColor Gray
Copy-Item -LiteralPath $DistJar -Destination (Join-Path $OutputDir "Restaurante.jar") -Force

# 4.2 Copiar librerías de tiempo de ejecución obligatorias
Write-Host "  -> Copiando dependencias en lib/..." -ForegroundColor Gray
$libreriasRuntime = @(
    "AbsoluteLayout.jar",
    "itextpdf-5.5.1.jar",
    "mysql-connector-j-8.0.31.jar",
    "pdfbox-2.0.31.jar",
    "fontbox-2.0.31.jar",
    "commons-logging-1.2.jar"
)

foreach ($lib in $libreriasRuntime) {
    $origen = Join-Path $LibDir $lib
    if (-not (Test-Path -LiteralPath $origen)) {
        # Fallback a dist/lib si existiera
        $origenDist = Join-Path (Join-Path $ProjectRoot "dist\lib") $lib
        if (Test-Path -LiteralPath $origenDist) {
            $origen = $origenDist
        }
    }

    if (Test-Path -LiteralPath $origen) {
        Copy-Item -LiteralPath $origen -Destination (Join-Path (Join-Path $OutputDir "lib") $lib) -Force
    } else {
        Write-Host "  [ERROR] No se encontro la libreria requerida: $lib" -ForegroundColor Red
        exit 1
    }
}

# 4.3 Copiar solo el esquema de instalación nueva. Las actualizaciones usan el migrador JDBC versionado.
Write-Host "  -> Copiando esquema de instalación nueva..." -ForegroundColor Gray
Copy-Item -LiteralPath (Join-Path $ProjectRoot "BD.sql") -Destination (Join-Path $OutputDir "BD.sql") -Force

# 4.4 Copiar scripts de automatización e instalación
Write-Host "  -> Copiando scripts de inicio e instaladores..." -ForegroundColor Gray
$scripts = @(
    "iniciar_restaurante.bat",
    "iniciar_restaurante.sh",
    "iniciar_restaurante_silencioso.vbs",
    "instalar_acceso_directo.bat",
    "instalar_acceso_directo.ps1",
    "instalacion_completa_un_clic.bat",
    "icono_restaurante.ico",
    "icono_restaurante.png",
    "actualizar_bd.bat",
    "instalar_mysql_y_bd.bat",
    "instalar_mysql_y_bd.ps1",
    "configurar_mysql.ps1",
    "listar_impresoras.bat"
)

foreach ($s in $scripts) {
    $origenScript = Join-Path $ProjectRoot $s
    if (-not (Test-Path -LiteralPath $origenScript)) {
        # Fallback a carpeta plantilla previa si estaba dentro de Restaurante_Ejecutable
        $origenScript = Join-Path $ProjectRoot "Restaurante_Ejecutable\$s"
    }
    if (Test-Path -LiteralPath $origenScript) {
        Copy-Item -LiteralPath $origenScript -Destination (Join-Path $OutputDir $s) -Force
    }
}

# 4.5 Copiar documentación y archivo .env
Write-Host "  -> Copiando guias y configuracion .env..." -ForegroundColor Gray
$docs = @(
    "LEEME_INSTRUCCIONES.txt",
    "RESUMEN_CAMBIOS_Y_USO.txt"
)
foreach ($doc in $docs) {
    $origenDoc = Join-Path $ProjectRoot $doc
    if (Test-Path -LiteralPath $origenDoc) {
        Copy-Item -LiteralPath $origenDoc -Destination (Join-Path $OutputDir $doc) -Force
    }
}

# Archivo .env
$envOrigen = Join-Path $ProjectRoot ".env"
$envDestino = Join-Path $OutputDir ".env"
if (Test-Path -LiteralPath $envOrigen) {
    Copy-Item -LiteralPath $envOrigen -Destination $envDestino -Force
} else {
    # Generar .env estándar por defecto si no existe
    $envContenido = @"
# ========================================================
# Configuración de Conexión a Base de Datos MySQL
# ========================================================
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=restaurante
MYSQL_USER=admin
MYSQL_PASSWORD=admin

# Variables alternativas compatibles
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=restaurante
DB_USER=admin
DB_PASSWORD=admin
"@
    Set-Content -LiteralPath $envDestino -Value $envContenido -Encoding UTF8
}

# -----------------------------------------------------------------------------
# 5. VALIDACIÓN DE INTEGRIDAD FÍSICA (100% FUNCIONAL)
# -----------------------------------------------------------------------------
Write-Host "`n[5/6] Validando integridad funcional de la carpeta regenerada..." -ForegroundColor Cyan

$errores = 0

# Verificar Restaurante.jar
$jarDestino = Join-Path $OutputDir "Restaurante.jar"
if (-not (Test-Path -LiteralPath $jarDestino) -or ((Get-Item -LiteralPath $jarDestino).Length -lt 100000)) {
    Write-Host "  [FALLA] 'Restaurante.jar' falta o es menor a 100KB." -ForegroundColor Red
    $errores++
} else {
    $tamJarMb = [math]::Round(((Get-Item -LiteralPath $jarDestino).Length / 1MB), 2)
    Write-Host "  [OK] Restaurante.jar presente ($tamJarMb MB)." -ForegroundColor Green
}

# Verificar dependencias en lib/
foreach ($lib in $libreriasRuntime) {
    $rutaLib = Join-Path (Join-Path $OutputDir "lib") $lib
    if (-not (Test-Path -LiteralPath $rutaLib) -or ((Get-Item -LiteralPath $rutaLib).Length -eq 0)) {
        Write-Host "  [FALLA] Libreria lib/$lib falta o esta vacia." -ForegroundColor Red
        $errores++
    }
}
if ($errores -eq 0) {
    Write-Host "  [OK] Todas las dependencias en lib/ estan completas." -ForegroundColor Green
}

# Verificar BD.sql y scripts
$archivosCriticos = @("BD.sql", "actualizar_bd.sql", "iniciar_restaurante.bat", ".env")
foreach ($ac in $archivosCriticos) {
    $rutaAc = Join-Path $OutputDir $ac
    if (-not (Test-Path -LiteralPath $rutaAc)) {
        Write-Host "  [FALLA] Falta el archivo critico '$ac'." -ForegroundColor Red
        $errores++
    }
}

if ($errores -gt 0) {
    Write-Host "  [ERROR] Fallaron $errores verificaciones de integridad. Abortando empaquetado ZIP." -ForegroundColor Red
    exit 1
} else {
    Write-Host "  [OK] Validacion de integridad 100% superada." -ForegroundColor Green
}

# -----------------------------------------------------------------------------
# 6. GENERAR ARCHIVO ZIP DE PRODUCCIÓN
# -----------------------------------------------------------------------------
Write-Host "`n[6/6] Generando archivo comprimido ZIP..." -ForegroundColor Cyan

try {
    Compress-Archive -Path $OutputDir -DestinationPath $ZipFile -Force
    if (Test-Path -LiteralPath $ZipFile) {
        $tamZipMb = [math]::Round(((Get-Item -LiteralPath $ZipFile).Length / 1MB), 2)
        Write-Host "  [OK] Archivo ZIP generado exitosamente: $ZipFile ($tamZipMb MB)" -ForegroundColor Green
    } else {
        throw "No se genero el archivo ZIP en la ruta esperada."
    }
} catch {
    Write-Host "  [ERROR] Falla al comprimir el archivo ZIP: $_" -ForegroundColor Red
    exit 1
}

# -----------------------------------------------------------------------------
# RESUMEN FINAL
# -----------------------------------------------------------------------------
$conteoArchivos = (Get-ChildItem -LiteralPath $OutputDir -Recurse -File).Count
$tamTotalMb = [math]::Round(((Get-ChildItem -LiteralPath $OutputDir -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB), 2)

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "         PROCESO COMPLETADO EXITOSAMENTE - SISTEMA 100% FUNCIONAL               " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "  Carpeta Ejecutable : $OutputDir" -ForegroundColor White
Write-Host "  Archivos totales   : $conteoArchivos archivos ($tamTotalMb MB)" -ForegroundColor White
Write-Host "  Archivo Comprimido : $ZipFile ($tamZipMb MB)" -ForegroundColor White
Write-Host "--------------------------------------------------------------------------------" -ForegroundColor Gray
Write-Host "  Instrucciones para su socio / cliente en Windows 11:" -ForegroundColor Yellow
Write-Host "   1. Puede copiar o compartir 'Restaurante_Ejecutable.zip'." -ForegroundColor Gray
Write-Host "   2. Extraer en cualquier carpeta (ej: C:\Restaurante)." -ForegroundColor Gray
Write-Host "   3. Si es primera vez: ejecutar 'instalar_mysql_y_bd.bat'." -ForegroundColor Gray
Write-Host "   4. Si es actualizacion: ejecutar 'actualizar_bd.bat'." -ForegroundColor Gray
Write-Host "   5. Para iniciar el programa: doble clic en 'iniciar_restaurante.bat'." -ForegroundColor Gray
Write-Host "================================================================================" -ForegroundColor Green
Write-Host ""
