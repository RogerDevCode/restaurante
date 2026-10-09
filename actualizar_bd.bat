@echo off
setlocal
title Actualizador seguro de base de datos - Restaurante 2026
cd /d "%~dp0"

echo ========================================================
echo   ACTUALIZADOR SEGURO DE BASE DE DATOS - RESTAURANTE 2026
echo ========================================================
echo.

set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_CMD set "JAVA_CMD=%%J"
if not defined JAVA_CMD (
    echo [ERROR] No se encontro Java. Instale Java 21 y vuelva a ejecutar.
    set "EXIT_CODE=1"
    goto :finish
)

set "APP_DIR=%~dp0"
set "APP_JAR=%APP_DIR%Restaurante.jar"
set "APP_LIB=%APP_DIR%lib"
if not exist "%APP_JAR%" if exist "%APP_DIR%dist\Restaurante.jar" (
    set "APP_JAR=%APP_DIR%dist\Restaurante.jar"
    if exist "%APP_DIR%dist\lib" (set "APP_LIB=%APP_DIR%dist\lib") else set "APP_LIB=%APP_DIR%lib"
)
if not exist "%APP_JAR%" if exist "%APP_DIR%Restaurante_Ejecutable\Restaurante.jar" (
    set "APP_DIR=%APP_DIR%Restaurante_Ejecutable\"
    set "APP_JAR=%APP_DIR%Restaurante.jar"
    set "APP_LIB=%APP_DIR%lib"
)
if not exist "%APP_JAR%" (
    echo [ERROR] No se encontro Restaurante.jar. Compile o genere el paquete antes de migrar.
    set "EXIT_CODE=1"
    goto :finish
)
if not exist "%APP_DIR%.env" (
    echo [ERROR] No se encontro .env. La actualizacion no usara credenciales supuestas.
    set "EXIT_CODE=1"
    goto :finish
)

echo [INFO] Se creara y verificara un respaldo antes de cualquier cambio de esquema.
echo [INFO] Una base desconocida o incompleta se rechazara sin reconstruirla.
pushd "%APP_DIR%"
"%JAVA_CMD%" -cp "%APP_JAR%;%APP_LIB%\*" restaurante.Restaurante --migrate-db
set "JAVA_EXIT=%ERRORLEVEL%"
popd
if not "%JAVA_EXIT%"=="0" (
    echo.
    echo [ERROR] La migracion fallo. No se importo BD.sql. Revise logs y respaldos.
    set "EXIT_CODE=1"
    goto :finish
)

echo.
echo [OK] Migracion completada y postcondiciones verificadas.
set "EXIT_CODE=0"

:finish
echo.
pause
exit /b %EXIT_CODE%
