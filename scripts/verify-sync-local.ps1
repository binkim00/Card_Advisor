# Targeted regression and local runtime refresh. Does not enable CODEF or delete data.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$evidence=Get-Content -LiteralPath (Join-Path $root '.run/android-sync-verification.json') -Raw | ConvertFrom-Json
if(!$evidence.passed){throw 'Complete the Android sync verification before refreshing the runtime.'}
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
Get-Content -LiteralPath (Join-Path $root '.env') | ForEach-Object {if($_ -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}
$maven='C:\Users\SSAFY\.m2\wrapper\dists\apache-maven-3.9.5\2021cb71\bin\mvn.cmd'
$previousFlag=$env:RUN_FINANCE_PG_TESTS
$env:RUN_FINANCE_PG_TESTS='true'
Push-Location (Join-Path $root 'backend')
try{& $maven -B '-Dtest=FinancialSyncPostgresTest' test;if($LASTEXITCODE -ne 0){throw 'PostgreSQL sync regression failed'}}finally{$env:RUN_FINANCE_PG_TESTS=$previousFlag;Pop-Location}
$jar=Join-Path $root 'backend/target/backend-0.1.0.jar'
$pidFile=Join-Path $root '.run/backend.pid'
if(Test-Path -LiteralPath $pidFile){
 $backendProcessId=[int](Get-Content -LiteralPath $pidFile -Raw).Trim()
 $p=Get-CimInstance Win32_Process -Filter "ProcessId = $backendProcessId"
 if($p){if(!$p.CommandLine.Contains($jar.Replace('/','\'))){throw 'Recorded process is not this project backend; refusing to stop it.'};Stop-Process -Id $backendProcessId;Wait-Process -Id $backendProcessId -ErrorAction SilentlyContinue}
}
Push-Location (Join-Path $root 'backend')
try{& $maven -B '-DskipTests' package;if($LASTEXITCODE -ne 0){throw 'Backend packaging failed'}}finally{Pop-Location}
& (Join-Path $PSScriptRoot 'start-backend.ps1')
for($attempt=0;$attempt -lt 15;$attempt++){
 try{$health=Invoke-RestMethod http://127.0.0.1:18080/v1/health -TimeoutSec 2;if($health.status -eq 'UP'){break}}catch{}
 Start-Sleep -Seconds 1
}
if(!$health -or $health.status -ne 'UP'){throw 'Local backend is not healthy'}
$record=@{health=$health.status;realFinancialIntegration=$health.realFinancialIntegration;jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower();postgresSyncTests=14;syntheticOnly=$true}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root '.run/android-sync-runtime.json') -Encoding utf8
Write-Output 'PostgreSQL sync regression passed and local API restarted.'
