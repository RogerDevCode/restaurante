[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$sqlFile = Join-Path $scriptDir "BD.sql"
if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) {
    throw "No se encontró BD.sql junto al instalador."
}

$mysqlCmd = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
if (-not $mysqlCmd) {
    $candidatos = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MariaDB 11.4\bin\mysql.exe",
        "C:\Program Files\MariaDB 10.11\bin\mysql.exe",
        "C:\xampp\mysql\bin\mysql.exe",
        "C:\laragon\bin\mysql\mysql\bin\mysql.exe"
    )
    foreach ($path in $candidatos) {
        if (Test-Path -LiteralPath $path) { $mysqlCmd = $path; break }
    }
}
if (-not $mysqlCmd) { throw "No se localizó mysql.exe. Instale MySQL o MariaDB y vuelva a ejecutar." }

$service = Get-Service -Name "MySQL*", "MariaDB*" -ErrorAction SilentlyContinue |
    Where-Object { $_.Status -ne "Running" } | Select-Object -First 1
if ($service) {
    Start-Service -Name $service.Name
    Start-Sleep -Seconds 2
}

function New-ClientOptionFile([string]$user, [string]$password, [string]$hostName = "127.0.0.1") {
    $file = Join-Path $env:TEMP ("restaurante-mysql-" + [guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $password.Replace("\", "\\").Replace('"', '\"').Replace("`r", "").Replace("`n", "")
    $lines = [string[]]@("[client]", "user=$user", "password=`"$escaped`"", "host=$hostName")
    [System.IO.File]::WriteAllLines($file, $lines, [System.Text.UTF8Encoding]::new($false))
    $acl = Get-Acl -LiteralPath $file
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, "FullControl", "Allow")
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $file -AclObject $acl
    return $file
}

function Invoke-MySql([string]$optionFile, [string[]]$arguments, [string]$sql) {
    if ($null -eq $sql) {
        $output = & $mysqlCmd "--defaults-extra-file=$optionFile" @arguments 2>&1
    } else {
        $output = $sql | & $mysqlCmd "--defaults-extra-file=$optionFile" @arguments 2>&1
    }
    $code = $LASTEXITCODE
    return @{ Code = $code; Output = ($output | Out-String) }
}

function New-RandomDatabasePassword {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) }
    finally { $generator.Dispose() }
    return ([BitConverter]::ToString($bytes) -replace "-", "").ToLowerInvariant()
}

function Set-EnvPassword([string]$path, [string]$password) {
    $content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $keys = @("DB_PASSWORD", "MYSQL_PASSWORD", "DB_PASS")
    $updated = $false
    foreach ($key in $keys) {
        $pattern = '(?m)^\s*' + [regex]::Escape($key) + '\s*=.*$'
        if ([regex]::IsMatch($content, $pattern)) {
            $content = [regex]::Replace($content, $pattern, "$key=$password")
            $updated = $true
        }
    }
    if (-not $updated) {
        $content = $content.TrimEnd("`r", "`n") + "`r`nMYSQL_PASSWORD=$password`r`n"
    }
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

$rootSecure = Read-Host "Ingrese la contraseña ROOT de MySQL (Enter si no tiene)" -AsSecureString
$rootPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootSecure)
$rootFile = $null
try {
    $rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rootPtr)
    $rootFile = New-ClientOptionFile "root" $rootPassword "localhost"
    $probe = Invoke-MySql $rootFile @("-N", "-B", "-e", "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='restaurante'") $null
    if ($probe.Code -ne 0) { throw "No se pudo verificar la existencia de restaurante como root. No se modificó la base." }
    if ($probe.Output.Trim() -ne "0") {
        throw "La base restaurante ya existe. Este instalador es solo para una base nueva y no la sobrescribirá. Para actualizar use actualizar_bd.bat."
    }

    $envFile = Join-Path $scriptDir ".env"
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw "No se encontró .env. Configure las credenciales antes de crear la base nueva."
    }
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and -not $line.TrimStart().StartsWith("#")) {
            $settings[$matches[1]] = $matches[2].Trim([char[]]@(34, 39))
        }
    }
    $dbUser = if ($settings.ContainsKey("DB_USER")) { $settings["DB_USER"] } elseif ($settings.ContainsKey("MYSQL_USER")) { $settings["MYSQL_USER"] } else { "restaurante_app" }
    $dbPassword = if ($settings.ContainsKey("DB_PASSWORD")) { $settings["DB_PASSWORD"] } elseif ($settings.ContainsKey("MYSQL_PASSWORD")) { $settings["MYSQL_PASSWORD"] } elseif ($settings.ContainsKey("DB_PASS")) { $settings["DB_PASS"] } else { $null }
    if ([string]::IsNullOrWhiteSpace($dbPassword) -or $dbPassword -eq "CAMBIAR_POR_CLAVE_LOCAL_APP" -or $dbPassword -eq "admin") {
        $dbPassword = New-RandomDatabasePassword
        Set-EnvPassword $envFile $dbPassword
        Write-Host "Se generó una contraseña aleatoria de aplicación y se guardó en .env."
    }
    if ([string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrEmpty($dbPassword)) {
        throw "La configuración .env no contiene usuario y contraseña de aplicación válidos."
    }
    if ($dbUser -notmatch '^[A-Za-z0-9_]{1,32}$' -or $dbPassword.Contains("`r") -or $dbPassword.Contains("`n")) {
        throw "Las credenciales de .env contienen caracteres no admitidos por el instalador inicial."
    }
    $quote = { param([string]$value) $value.Replace("\", "\\").Replace("'", "''") }
    $userProbeSql = "SELECT COUNT(*) FROM mysql.user WHERE user='$(& $quote $dbUser)' AND host='127.0.0.1'"
    $userProbe = Invoke-MySql $rootFile @("-N", "-B", "-e", $userProbeSql) $null
    if ($userProbe.Code -ne 0 -or $userProbe.Output.Trim() -ne "0") {
        throw "El usuario MySQL '$dbUser'@'127.0.0.1' ya existe o no se pudo verificar. Cambie DB_USER/MYSQL_USER en .env; no se alterará ninguna cuenta existente."
    }
    $sqlCreate = "CREATE DATABASE restaurante CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; " +
        "CREATE USER '$(& $quote $dbUser)'@'127.0.0.1' IDENTIFIED BY '$(& $quote $dbPassword)'; " +
        "GRANT ALL PRIVILEGES ON restaurante.* TO '$(& $quote $dbUser)'@'127.0.0.1';"
    $create = Invoke-MySql $rootFile @("-e", $sqlCreate) $null
    if ($create.Code -ne 0) { throw "No se pudo crear la base nueva y su usuario de aplicación. $($create.Output)" }

    $appFile = New-ClientOptionFile $dbUser $dbPassword
    try {
        $sql = Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8
        $import = Invoke-MySql $appFile @("restaurante") $sql
        if ($import.Code -ne 0) { throw "Falló la carga inicial de BD.sql (código $($import.Code)). No vuelva a importar sobre esta base; conserve logs y solicite recuperación. $($import.Output)" }
    } finally {
        Remove-Item -LiteralPath $appFile -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Esquema inicial cargado en una base que no existía. Usuario de aplicación: $dbUser" -ForegroundColor Green
} finally {
    if ($rootFile) { Remove-Item -LiteralPath $rootFile -Force -ErrorAction SilentlyContinue }
    if ($rootPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rootPtr) }
}
