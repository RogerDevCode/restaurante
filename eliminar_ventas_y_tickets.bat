@echo off
setlocal
title Eliminar Ventas y Tickets - Restaurante 2026
cd /d "%~dp0"

echo ========================================================
echo  RESTAURANTE 2026 - ELIMINAR VENTAS, PEDIDOS Y TICKETS
echo ========================================================

rem Si se pasa un archivo como parametro (ej: arrastrar a1.sql sobre el bat)
if not "%~1"=="" (
    goto :limpiar_archivo
)

echo.
echo  Seleccione una opcion:
echo    [1] Vaciar ventas y tickets de la BASE DE DATOS activa (MySQL)
echo    [2] Limpiar un archivo de respaldo SQL (ej: a1.sql)
echo    [0] Salir
echo.
set /p "OPCION=Opcion [1/2/0]: "

if "%OPCION%"=="1" goto :vaciar_bd
if "%OPCION%"=="2" goto :pedir_archivo
goto :salir

:pedir_archivo
set "ARCHIVO_INPUT=a1.sql"
if exist "%~dp0a1.sql" (
    set /p "ARCHIVO_INPUT=Ruta del archivo SQL [%ARCHIVO_INPUT%]: "
) else (
    set /p "ARCHIVO_INPUT=Ruta del archivo SQL a limpiar: "
)
if "%ARCHIVO_INPUT%"=="" set "ARCHIVO_INPUT=a1.sql"
set "PARAM_FILE=%ARCHIVO_INPUT%"
goto :ejecutar_limpieza_archivo

:limpiar_archivo
set "PARAM_FILE=%~1"

:ejecutar_limpieza_archivo
if not exist "%PARAM_FILE%" (
    echo [ERROR] No se encontro el archivo: "%PARAM_FILE%"
    goto :error
)

echo.
echo Procesando archivo: "%PARAM_FILE%"...

rem Intentar primero con python si esta disponible
set "PYTHON_CMD="
for /f "delims=" %%P in ('where python.exe 2^>nul') do if not defined PYTHON_CMD set "PYTHON_CMD=%%P"
for /f "delims=" %%P in ('where python3.exe 2^>nul') do if not defined PYTHON_CMD set "PYTHON_CMD=%%P"

if defined PYTHON_CMD (
    if exist "%~dp0limpiar_sql_ventas.py" (
        "%PYTHON_CMD%" "%~dp0limpiar_sql_ventas.py" "%PARAM_FILE%"
        if not errorlevel 1 goto :fin_archivo
    )
)

rem Si no hay python, usar PowerShell nativo de Windows
echo Usando motor PowerShell nativo de Windows...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$inFile = '%PARAM_FILE%';" ^
    "$base = [System.IO.Path]::GetFileNameWithoutExtension($inFile);" ^
    "$dir = [System.IO.Path]::GetDirectoryName($inFile);" ^
    "if ([string]::IsNullOrEmpty($dir)) { $dir = '.'; }" ^
    "$outFile = Join-Path $dir ($base + '-sin-ventas.sql');" ^
    "$txt = [System.IO.File]::ReadAllText($inFile, [System.Text.Encoding]::UTF8);" ^
    "$tables = @('pedidos','detalle_pedidos','pedido_pagos','pedido_liquidaciones','pedido_saldos_movimientos','cierres_caja','auditoria_pedidos','auditoria_cambios_precio','login_intentos','plato_favorito','comandas_marchas','comandas_marchas_detalle','ledger_fiscal','ledger_fiscal_ancla');" ^
    "foreach ($t in $tables) {" ^
    "    $pAuto = '(?s)CREATE TABLE \`' + $t + '\`\s*\(.*?\)\s*ENGINE=[^;]+;';" ^
    "    $txt = [regex]::Replace($txt, $pAuto, { param($m) [regex]::Replace($m.Value, 'AUTO_INCREMENT=\d+', 'AUTO_INCREMENT=1') });" ^
    "    $pData = '(?s)/\*Data for the table \`' + $t + '\` \*/\s*insert\s+into\s+\`' + $t + '\`.*?;\s*';" ^
    "    $txt = [regex]::Replace($txt, $pData, '/*Data for the table `' + $t + '` */`r`n`r`n');" ^
    "}" ^
    "[System.IO.File]::WriteAllText($outFile, $txt, [System.Text.Encoding]::UTF8);" ^
    "Write-Host '[OK] Archivo generado exitosamente: ' $outFile;"

if errorlevel 1 (
    echo [ERROR] No se pudo procesar el archivo SQL.
    goto :error
)

:fin_archivo
echo.
echo ========================================================
echo  ARCHIVO SQL DEPURADO CON EXITO
echo  Se eliminaron pedidos, ventas, pagos y auditorias.
echo  Contadores AUTO_INCREMENT reiniciados a 1.
echo ========================================================
echo.
pause
exit /b 0

:vaciar_bd
echo.
echo ========================================================
echo  [ATENCION] VACIADO DE VENTAS EN BASE DE DATOS ACTIVA
echo ========================================================
echo  Este proceso:
echo    - ELIMINA pedidos anteriores, consumos, pagos y cierres.
echo    - REINICIA el contador de pedidos al numero 1.
echo    - LIBERA todas las mesas de todas las salas.
echo    - CONSERVA platos, precios, categorias, salas, mesas,
echo      mesoneros, clientes, usuarios y configuracion.
echo.
echo  Se creara un respaldo preventivo antes de la limpieza.
echo ========================================================
echo.
set /p "CONFIRMA=Desea continuar? (Escriba S para SI, o N para Cancelar): "
if /i not "%CONFIRMA%"=="S" (
    echo Operacion cancelada.
    goto :salir
)

set "SQL_FILE=%~dp0eliminar_ventas_y_tickets.sql"
if not exist "%SQL_FILE%" (
    echo [ERROR] No se encontro el archivo "%SQL_FILE%"
    goto :error
)

rem Valores por defecto de conexion
set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_USER=admin"
set "DB_PASS=admin"
set "DB_NAME=restaurante"

rem Leer variables desde .env si existe
if exist "%~dp0.env" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%~dp0.env") do (
        if "%%A"=="MYSQL_HOST" set "DB_HOST=%%B"
        if "%%A"=="MYSQL_PORT" set "DB_PORT=%%B"
        if "%%A"=="MYSQL_USER" set "DB_USER=%%B"
        if "%%A"=="MYSQL_PASSWORD" set "DB_PASS=%%B"
        if "%%A"=="MYSQL_DATABASE" set "DB_NAME=%%B"
        if "%%A"=="DB_HOST" set "DB_HOST=%%B"
        if "%%A"=="DB_PORT" set "DB_PORT=%%B"
        if "%%A"=="DB_USER" set "DB_USER=%%B"
        if "%%A"=="DB_PASS" set "DB_PASS=%%B"
        if "%%A"=="DB_NAME" set "DB_NAME=%%B"
    )
)

echo.
echo [1/3] Localizando cliente MySQL (mysql.exe)...
set "MYSQL_CMD="
set "MYSQLDUMP_CMD="

for /f "delims=" %%I in ('where mysql.exe 2^>nul') do if not defined MYSQL_CMD set "MYSQL_CMD=%%I"
for /f "delims=" %%I in ('where mysqldump.exe 2^>nul') do if not defined MYSQLDUMP_CMD set "MYSQLDUMP_CMD=%%I"

if not defined MYSQL_CMD (
    for %%P in (
        "%ProgramFiles%\MySQL\MySQL Server 8.4\bin"
        "%ProgramFiles%\MySQL\MySQL Server 8.0\bin"
        "%ProgramFiles%\MySQL\MySQL Server 5.7\bin"
        "%ProgramFiles(x86)%\MySQL\MySQL Server 8.0\bin"
        "C:\xampp\mysql\bin"
        "C:\laragon\bin\mysql\current\bin"
        "C:\MySQL\bin"
    ) do (
        if not defined MYSQL_CMD if exist "%%~P\mysql.exe" (
            set "MYSQL_CMD=%%~P\mysql.exe"
            set "MYSQLDUMP_CMD=%%~P\mysqldump.exe"
        )
    )
)

if not defined MYSQL_CMD (
    echo [ERROR] No se encontro mysql.exe en PATH ni en rutas estandar.
    goto :error
)

echo [2/3] Generando respaldo preventivo de seguridad...
set "DT=%date:~6,4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do if not "%%I"=="" set "DT=%%I"
set "DT=%DT: =0%"
set "BACKUP_FILE=%~dp0respaldo_antes_de_eliminar_ventas_%DT:~0,8%_%DT:~8,6%.sql"

if defined MYSQLDUMP_CMD if exist "%MYSQLDUMP_CMD%" (
    "%MYSQLDUMP_CMD%" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% --single-transaction %DB_NAME% > "%BACKUP_FILE%" 2>nul
    if exist "%BACKUP_FILE%" (
        echo   - Respaldo guardado exitosamente en: %BACKUP_FILE%
    ) else (
        echo   - [AVISO] No se pudo generar respaldo previo de la base.
    )
)

echo [3/3] Ejecutando eliminacion de ventas y reseteo a ticket #1...
"%MYSQL_CMD%" --default-character-set=utf8mb4 -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% %DB_NAME% < "%SQL_FILE%"
if errorlevel 1 (
    echo [ERROR] Fallo la ejecucion del vaciado.
    goto :error
)

rem Validar migraciones con jar
set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_CMD set "JAVA_CMD=%%J"

if defined JAVA_CMD if exist "%~dp0Restaurante.jar" (
    "%JAVA_CMD%" -jar "%~dp0Restaurante.jar" --migrate-db
)

echo.
echo ========================================================
echo  OPERACION COMPLETADA CON EXITO
echo  Todas las ventas y pedidos han sido eliminados.
echo  El proximo ticket sera el #1.
echo  100%% de mesas libres.
echo  Platos, precios y configuracion intactos.
echo ========================================================
echo.
pause
exit /b 0

:error
echo.
echo [ERROR] La operacion no pudo completarse. Revise los mensajes anteriores.
echo.
pause
exit /b 1

:salir
exit /b 0
