@echo off
rem ==============================================================================
rem CONFIGURADOR DE TUNEL INVERSO SSH ZERO-TRUST - RESTAURANTE 2026 (WINDOWS)
rem Genera par de claves Ed25519 y directivas de confinamiento estricto para el VPS
rem ==============================================================================

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "DIR_CLAVES=%SCRIPT_DIR%capip_claves_maestras"
set "CLAVE_PRIVADA=%DIR_CLAVES%\tunel_ed25519"
set "CLAVE_PUBLICA=%DIR_CLAVES%\tunel_ed25519.pub"

echo ================================================================================
echo     CONFIGURACION DE TUNEL INVERSO SSH ZERO-TRUST (MONITOREO REMOTO)
echo ================================================================================

if not exist "%DIR_CLAVES%" (
    mkdir "%DIR_CLAVES%"
)

if not exist "%CLAVE_PRIVADA%" (
    echo [1/3] Generando par de claves Ed25519 para telemetria remota...
    ssh-keygen -t ed25519 -f "%CLAVE_PRIVADA%" -N "" -C "restaurante-telemetria-remota" >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] No se pudo ejecutar ssh-keygen. Verifique que OpenSSH Client este instalado.
        goto :fin
    )
    echo   -^> Clave privada generada: %CLAVE_PRIVADA%
    echo   -^> Clave publica generada: %CLAVE_PUBLICA%
) else (
    echo [1/3] Clave Ed25519 existente detectada:
    echo   -^> %CLAVE_PRIVADA%
)

set "CONTENIDO_PUB="
if exist "%CLAVE_PUBLICA%" (
    set /p CONTENIDO_PUB=<"%CLAVE_PUBLICA%"
)

echo.
echo ================================================================================
echo  [PASO OBLIGATORIO EN TU SERVIDOR BASTION EN LA NUBE (VPS)]
echo ================================================================================
echo En tu servidor VPS (Ubuntu/Debian), ejecuta los siguientes comandos como root:
echo.
echo   1. Crear el usuario del tunel sin privilegios ni terminal interactiva:
echo      useradd -m -s /bin/false telemetria
echo      mkdir -p /home/telemetria/.ssh
echo      chmod 700 /home/telemetria/.ssh
echo.
echo   2. Agregar EXACTAMENTE la siguiente linea confinada en:
echo      /home/telemetria/.ssh/authorized_keys
echo.
echo --------------------------------------------------------------------------------
echo command="/bin/false",no-agent-forwarding,no-user-rc,no-X11-forwarding,no-pty,permitopen="127.0.0.1:8080" %CONTENIDO_PUB%
echo --------------------------------------------------------------------------------
echo.
echo   3. Ajustar permisos en el VPS:
echo      chmod 600 /home/telemetria/.ssh/authorized_keys
echo      chown -R telemetria:telemetria /home/telemetria/.ssh
echo.
echo ================================================================================
echo  [PASO LOCAL EN ESTE RESTAURANTE]
echo ================================================================================
echo Configura en tu archivo .env las siguientes variables:
echo.
echo   TUNEL_SSH_HABILITADO=true
echo   TUNEL_SSH_HOST=^<IP_O_DOMINIO_DE_TU_VPS^>
echo   TUNEL_SSH_PUERTO=22
echo   TUNEL_SSH_USUARIO=telemetria
echo   TUNEL_SSH_PUERTO_REMOTO=8443
echo   TUNEL_SSH_CLAVE_PRIVADA=%CLAVE_PRIVADA%
echo   TUNEL_SSH_TOKEN_ADMIN=secreto_gerencial_123
echo.
echo Al reiniciar la aplicacion, el tunel se conectara automaticamente y el dueno
echo podra ver sus ventas en vivo en su celular abriendo:
echo   http://^<IP_DE_TU_VPS^>:8443/gerencia
echo ================================================================================

:fin
endlocal
