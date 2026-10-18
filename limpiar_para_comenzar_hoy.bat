@echo off
setlocal
title Limpiar Base de Datos para Comenzar Hoy - Restaurante 2026
cd /d "%~dp0"

echo ========================================================
echo  RESTAURANTE 2026 - LIMPIEZA PARA COMENZAR HOY
echo ========================================================
echo.
echo  Este proceso prepara el sistema para iniciar operaciones:
echo    - ELIMINA pedidos anteriores, consumos, pagos y cierres.
echo    - REINICIA el contador de pedidos al numero 1.
echo    - LIBERA todas las mesas de todas las salas.
echo    - CONSERVA intactos los platos, precios, categorias,
echo      mesoneros, clientes, usuarios y configuracion.
echo.
echo  Se creara un respaldo preventivo antes de la limpieza.
echo ========================================================
echo.
echo  Presione cualquier tecla para CONTINUAR...
echo  (O cierre esta ventana si desea CANCELAR)
pause >nul

set "SQL_NAME=limpiar_para_comenzar_hoy.sql"
set "SQL_FILE=%~dp0%SQL_NAME%"

if not exist "%SQL_FILE%" (
    if exist "%SQL_NAME%" (
        set "SQL_FILE=%SQL_NAME%"
    )
)

if not exist "%SQL_FILE%" (
    echo [ERROR] No se encontro el archivo "%SQL_NAME%"
    echo en la carpeta: "%~dp0"
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
echo [1/4] Localizando cliente MySQL (mysql.exe)...
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
    echo [ERROR] No se encontro mysql.exe en PATH ni en las rutas estandar.
    echo Asegurese de que MySQL este instalado o agregue su carpeta bin al PATH.
    goto :error
)

echo   - mysql.exe: "%MYSQL_CMD%"

echo [2/4] Generando respaldo preventivo de seguridad...
set "DT=%date:~6,4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do if not "%%I"=="" set "DT=%%I"
set "DT=%DT: =0%"
set "BACKUP_FILE=%~dp0respaldo_antes_de_limpiar_%DT:~0,8%_%DT:~8,6%.sql"

if defined MYSQLDUMP_CMD if exist "%MYSQLDUMP_CMD%" (
    "%MYSQLDUMP_CMD%" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% --single-transaction %DB_NAME% > "%BACKUP_FILE%" 2>nul
    if exist "%BACKUP_FILE%" (
        echo   - Respaldo guardado exitosamente en: %BACKUP_FILE%
    ) else (
        echo   - [AVISO] No se pudo generar respaldo previo de la base.
    )
)

echo [3/4] Ejecutando limpieza transaccional y reseteo de pedidos...
"%MYSQL_CMD%" --default-character-set=utf8mb4 -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% %DB_NAME% < "%SQL_FILE%"
if errorlevel 1 (
    echo [ERROR] Fallo la ejecucion de la limpieza. Verifique credenciales y permisos.
    goto :error
)
echo   - Tablas transaccionales limpiadas correctamente.
echo   - Contadores de pedidos reiniciados a 1.

echo [4/4] Validando migraciones de esquema con Restaurante.jar...
set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_CMD set "JAVA_CMD=%%J"

if defined JAVA_CMD if exist "%~dp0Restaurante.jar" (
    "%JAVA_CMD%" -jar "%~dp0Restaurante.jar" --migrate-db
)

echo.
echo ========================================================
echo  LIMPIEZA COMPLETADA CON EXITO
echo ========================================================
echo  El sistema esta listo para comenzar operaciones hoy:
echo    - Pedidos anteriores eliminados.
echo    - Siguiente pedido: #1.
echo    - Mesas disponibles: 100%%.
echo    - Platos y catalogo: Intactos con fecha de hoy.
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
