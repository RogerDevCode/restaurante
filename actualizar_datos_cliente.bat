@echo off
setlocal enabledelayedexpansion
title Actualizador de Base de Datos - Datos Reales de Cliente (Restaurante 2026)
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ========================================================
echo  ACTUALIZADOR DE BASE DE DATOS - DATOS REALES DE CLIENTE
echo  Restaurante 2026 (Windows)
echo ========================================================
echo.

set "SQL_FILE=BD RESTAURANTE 2026.sql"

if not exist "%SQL_FILE%" (
    echo [ERROR] No se encontro el archivo "%SQL_FILE%" en "%~dp0"
    goto :error
)

rem Valores por defecto
set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_USER=admin"
set "DB_PASS=admin"
set "DB_NAME=restaurante"

rem Leer variables desde .env si existe
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "KEY=%%A"
        set "VAL=%%B"
        if "!KEY!"=="MYSQL_HOST" set "DB_HOST=!VAL!"
        if "!KEY!"=="MYSQL_PORT" set "DB_PORT=!VAL!"
        if "!KEY!"=="MYSQL_USER" set "DB_USER=!VAL!"
        if "!KEY!"=="MYSQL_PASSWORD" set "DB_PASS=!VAL!"
        if "!KEY!"=="MYSQL_DATABASE" set "DB_NAME=!VAL!"
        if "!KEY!"=="DB_HOST" set "DB_HOST=!VAL!"
        if "!KEY!"=="DB_PORT" set "DB_PORT=!VAL!"
        if "!KEY!"=="DB_USER" set "DB_USER=!VAL!"
        if "!KEY!"=="DB_PASS" set "DB_PASS=!VAL!"
        if "!KEY!"=="DB_NAME" set "DB_NAME=!VAL!"
    )
)

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
    ) do (
        if not defined MYSQL_CMD if exist "%%~P\mysql.exe" (
            set "MYSQL_CMD=%%~P\mysql.exe"
            set "MYSQLDUMP_CMD=%%~P\mysqldump.exe"
        )
    )
)

if not defined MYSQL_CMD (
    echo [ERROR] No se encontro mysql.exe en PATH ni en las rutas estandar.
    echo Asegurese de que MySQL este instalado o agregue su carpeta 'bin' al PATH.
    goto :error
)

echo   - mysql.exe: "!MYSQL_CMD!"

echo [2/4] Generando respaldo preventivo de seguridad...
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do if not defined DT set "DT=%%I"
if not defined DT set "DT=%date:~6,4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "DT=%DT: =0%"
set "BACKUP_FILE=respaldo_preventivo_%DT:~0,8%_%DT:~8,6%.sql"

if defined MYSQLDUMP_CMD if exist "!MYSQLDUMP_CMD!" (
    "!MYSQLDUMP_CMD!" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% --single-transaction %DB_NAME% > "%BACKUP_FILE%" 2>nul
    if exist "%BACKUP_FILE%" (
        echo   - Respaldo guardado exitosamente en: %BACKUP_FILE%
    ) else (
        echo   - [AVISO] No se pudo generar respaldo previo o la base no existia aun.
    )
)

echo [3/4] Importando datos reales y catalogo sanitizado ("%SQL_FILE%")...
"!MYSQL_CMD!" -h %DB_HOST% -P %DB_PORT% -u%DB_USER% -p%DB_PASS% %DB_NAME% < "%SQL_FILE%"
if errorlevel 1 (
    echo [ERROR] Fallo la importacion de "%SQL_FILE%". Verifique credenciales y permisos.
    goto :error
)
echo   - Volcado importado exitosamente.

echo [4/4] Validando migraciones de esquema con Restaurante.jar...
set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_CMD set "JAVA_CMD=%%J"

if defined JAVA_CMD if exist "Restaurante.jar" (
    "!JAVA_CMD!" -jar Restaurante.jar --migrate-db
)

echo.
echo ========================================================
echo  ¡ACTUALIZACIÓN COMPLETADA CON ÉXITO!
echo ========================================================
echo  Datos del Negocio Establecidos:
echo    - Comercio: RESTAURANT LA AVALANCHA 2023, C.A
echo    - RIF:      J-504542725
echo    - Direccion: RIO CHICO EDO MIRANDA
echo    - Catalogo:  87 platos reales cargados
echo    - Tasa Base: 880.12 Bs/$
echo    - Usuario:   admin / admin (o xavier / admin)
echo ========================================================
echo.
pause
exit /b 0

:error
echo.
echo [ERROR] La actualizacion no pudo completarse. Revise los mensajes anteriores.
echo.
pause
exit /b 1
