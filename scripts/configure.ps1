$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) { Write-Output 'Existing local configuration preserved.'; exit 0 }
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes)
[IO.File]::WriteAllText($envFile, "DB_PASSWORD=$password`nDB_URL=jdbc:postgresql://127.0.0.1:54329/card_advisor`nDB_USER=card_advisor`n")
Write-Output 'Local configuration created. Secret values are not printed.'

