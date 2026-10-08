@echo off
title Sistema de Restaurante 2026 - Iniciando
chcp 65001 >nul
cd /d "%~dp0"
cls

echo ========================================================
echo       SISTEMA DE GESTIÓN DE RESTAURANTE 2026
echo ========================================================
echo.

:: Configurar Java desde JAVA_HOME si no está en PATH
where java >nul 2>&1
if %errorlevel% neq 0 (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "PATH=%JAVA_HOME%\bin;%PATH%"
        )
    )
)

:: Verificar si Java está instalado
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java no está instalado o no se encuentra en el PATH ni JAVA_HOME.
    echo Por favor ejecute 'instalar_acceso_directo.bat' para configurar Java 21 automáticamente.
    echo.
    pause
    exit /b 1
)

:: Verificar archivo .env
if not exist ".env" (
    if exist ".env.example" (
        echo [INFO] Creando archivo .env a partir de .env.example...
        copy ".env.example" ".env" >nul
        echo [AVISO] Se ha generado el archivo .env. Si tu configuración de base de datos
        echo         es distinta a la por defecto, edita el archivo .env con el Bloc de Notas.
        echo.
    )
)

:: Verificar si existe el JAR distribuible en la carpeta actual
if exist "Restaurante.jar" (
    echo [INFO] Ejecutando Sistema desde Restaurante.jar...
    java -jar "Restaurante.jar"
    goto fin
)

:: Verificar si existe el JAR distribuible en dist/
if exist "dist\Restaurante.jar" (
    echo [INFO] Ejecutando Sistema desde dist\Restaurante.jar...
    java -jar "dist\Restaurante.jar"
    goto fin
)

:: Si no existe el JAR, intentar con Apache Ant o compilar directamente
where ant >nul 2>&1
if %errorlevel% equ 0 (
    echo [INFO] Compilando y ejecutando con Apache Ant...
    ant run
    goto fin
)

echo [INFO] No se encontró dist\Restaurante.jar ni Apache Ant.
echo Compilando clases con javac...
if not exist "build\classes" mkdir "build\classes"

javac -encoding UTF-8 -cp "librerias\AbsoluteLayout.jar;librerias\itextpdf-5.5.1.jar;librerias\mysql-connector-j-8.0.31.jar" -d "build\classes" src\infraestructura\*.java src\Modelo\*.java src\Servicio\*.java src\Controlador\*.java src\Vista\*.java src\restaurante\*.java

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Hubo un problema al compilar las fuentes.
    echo Revisa el archivo README.txt para más detalles.
    pause
    exit /b 1
)

:: Copiar recursos de imágenes si existen
xcopy /E /I /Y "src\Img" "build\classes\Img" >nul 2>&1

echo [INFO] Iniciando la aplicación...
java -cp "librerias\AbsoluteLayout.jar;librerias\itextpdf-5.5.1.jar;librerias\mysql-connector-j-8.0.31.jar;build\classes" restaurante.Restaurante

:fin
if %errorlevel% neq 0 (
    echo.
    echo [AVISO] La aplicación finalizó con código de error.
    echo Revisa los archivos en la carpeta logs\ para más información.
    pause
)
