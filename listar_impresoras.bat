@echo off
setlocal
title Impresoras Instaladas en Windows 11 - Restaurante 2026
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo   LISTA DE IMPRESORAS DETECTADAS EN WINDOWS 11
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$printers = Get-CimInstance Win32_Printer;" ^
    "if ($printers) {" ^
    "    $printers | Select-Object @{Name='NOMBRE IMPRESORA';Expression={$_.Name}}, @{Name='PREDETERMINADA';Expression={$_.Default}}, @{Name='PUERTO';Expression={$_.PortName}}, @{Name='CONTROLADOR';Expression={$_.DriverName}} | Format-Table -AutoSize;" ^
    "} else {" ^
    "    Write-Host 'No se detectaron impresoras instaladas en el sistema.' -ForegroundColor Yellow;" ^
    "}"

echo ========================================================
echo  INSTRUCCIONES PARA VINCULAR CON EL SISTEMA:
echo ========================================================
echo  1. Localice el NOMBRE exacto de su tickera de 80 mm
echo     (Ejemplo: POS-80, EPSON TM-T20, Xprinter, etc.)
echo.
echo  2. OPCIÓN RECOMENDADA (Impresora Predeterminada):
echo     En Windows: Inicio ^> Configuracion ^> Impresoras
echo     Haga clic en la tickera y 'Establecer como predeterminada'.
echo     El programa imprimira alli automaticamente.
echo.
echo  3. OPCIÓN ESPECÍFICA (En archivo .env):
echo     Abra el archivo .env con el bloc de notas y agregue:
echo        IMPRESORA_TICKETS=NombreExactoDeSuTickera
echo        ACCION_IMPRESION=IMPRIMIR
echo ========================================================
echo.
pause
