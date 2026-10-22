$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$private=Join-Path $projectRoot '.local'
$config=Join-Path $private 'checkout-pg.properties';$composeEnv=Join-Path $private 'test-db.env'
if((Test-Path -LiteralPath $config) -or (Test-Path -LiteralPath $composeEnv)){throw 'Existing test configuration preserved. Reuse its database; no file was overwritten.'}
New-Item -ItemType Directory -Path $private -Force|Out-Null
$secret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$utf8=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($config,"DB_URL=jdbc:postgresql://127.0.0.1:54330/card_advisor_test`nDB_USER=synthetic`nDB_PASSWORD=$secret`n",$utf8)
[IO.File]::WriteAllText($composeEnv,"TEST_DB_PASSWORD=$secret`n",$utf8)
$owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
foreach($file in @($config,$composeEnv)){& icacls.exe $file /inheritance:r /grant:r "${owner}:F" '*S-1-5-18:F'|Out-Null;if($LASTEXITCODE -ne 0){throw 'Could not restrict test configuration permissions.'}}
$secret=$null
Write-Output 'Private test configuration created. Start compose.test.yaml with .local/test-db.env. No external provider settings were copied.'
