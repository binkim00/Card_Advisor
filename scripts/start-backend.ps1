$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Get-Content -LiteralPath (Join-Path $projectRoot '.env') | ForEach-Object { if($_ -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')} }
$jar=Join-Path $projectRoot 'backend\target\backend-0.1.0.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Build the backend package first.'}
$runtimeDir=Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$proc=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.11\bin\java.exe' -ArgumentList @('-jar', $jar) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'backend.log') -RedirectStandardError (Join-Path $runtimeDir 'backend-error.log')
$proc.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'backend.pid')
Write-Output ('Backend started with PID '+$proc.Id)

