' ==============================================================================
' LANZADOR SILENCIOSO - SISTEMA DE RESTAURANTE 2026 (WINDOWS 11)
' Ejecuta la aplicacion sin mostrar ventanas negras de consola (CMD)
' ==============================================================================
Option Explicit

Dim objShell, objFSO, strDir, strJavaExe, strJarPath, strJavaHome, intReturn

Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' 1. Fijar directorio de trabajo en la carpeta donde reside este script
strDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
objShell.CurrentDirectory = strDir

' 2. Localizar archivo JAR principal
If objFSO.FileExists(strDir & "\Restaurante.jar") Then
    strJarPath = """" & strDir & "\Restaurante.jar"""
ElseIf objFSO.FileExists(strDir & "\dist\Restaurante.jar") Then
    strJarPath = """" & strDir & "\dist\Restaurante.jar"""
Else
    MsgBox "No se encontro el archivo 'Restaurante.jar' en el directorio:" & vbCrLf & _
           strDir & vbCrLf & vbCrLf & _
           "Por favor asegurese de descomprimir todos los archivos del sistema.", _
           vbCritical, "Restaurante 2026 - Error de Inicio"
    WScript.Quit 1
End If

' 3. Determinar el ejecutable de Java (preferir javaw.exe para ejecucion sin consola)
strJavaExe = "javaw.exe"

' Revisar si JAVA_HOME esta configurado
strJavaHome = objShell.ExpandEnvironmentStrings("%JAVA_HOME%")
If strJavaHome <> "%JAVA_HOME%" And strJavaHome <> "" Then
    If objFSO.FileExists(strJavaHome & "\bin\javaw.exe") Then
        strJavaExe = """" & strJavaHome & "\bin\javaw.exe"""
    End If
End If

' 4. Ejecutar la aplicacion silenciosamente (0 = Oculto, False = No bloquear)
On Error Resume Next
intReturn = objShell.Run(strJavaExe & " -jar " & strJarPath, 0, False)

If Err.Number <> 0 Then
    Err.Clear
    ' Intento secundario con java.exe si javaw.exe no estuvo disponible
    intReturn = objShell.Run("java.exe -jar " & strJarPath, 0, False)
    If Err.Number <> 0 Then
        MsgBox "No se pudo iniciar Java 21." & vbCrLf & vbCrLf & _
               "Haga doble clic en 'instalar_acceso_directo.bat' para verificar y configurar Java automáticamente.", _
               vbExclamation, "Restaurante 2026 - Configuracion Requerida"
        WScript.Quit 1
    End If
End If
