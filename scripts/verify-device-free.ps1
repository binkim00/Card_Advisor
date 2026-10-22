param([switch]$Emulator,[string]$TestDatabaseConfig,[string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(!$TestDatabaseConfig){$TestDatabaseConfig=Join-Path $projectRoot '.local\checkout-pg.properties'}
if($Device -ne 'emulator-5554'){throw 'This command accepts emulator IDs only.'}
$run=Join-Path $projectRoot '.run';New-Item -ItemType Directory -Path $run -Force|Out-Null
$saved=@{};$names=@('DB_URL','DB_USER','DB_PASSWORD','CODEF_CONFIG_FILE','CODEF_STORAGE_KEY_FILE','OPENAI_CONFIG_FILE','ACCOUNT_MAIL_CONFIG_FILE','RUN_SERVICE_PG_TESTS','RUN_FINANCE_PG_TESTS','RUN_ACCOUNT_PG_TESTS','RUN_DEVICE_FREE_ANDROID_TEST','RUN_CHECKOUT_ANDROID_PG_TEST','RUN_ANDROID_LEDGER_DEVICE_TEST','ANDROID_TEST_DEVICE')
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$report=[ordered]@{passed=$false;physicalDevice=$false;actualProviderCalls=0;backend=$null;androidBuild=$false;emulator=$false;externalValidation='NOT_EXECUTED';sourceCommit=(& git -C $projectRoot rev-parse HEAD);workingTreeDirty=[bool](& git -C $projectRoot status --porcelain)}
function Run-Step([string]$Exe,[string[]]$Arguments,[string]$Log){& $Exe @Arguments *> (Join-Path $run $Log);if($LASTEXITCODE -ne 0){throw "Verification failed: .run/$Log"}}
try{
 Get-Content -LiteralPath $TestDatabaseConfig|ForEach-Object{if($_ -match '^(DB_URL|DB_USER|DB_PASSWORD)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}
 if($env:DB_URL -notmatch '^jdbc:postgresql://127\.0\.0\.1:54330/[A-Za-z0-9_]+$'){throw 'Use the separate synthetic PostgreSQL cluster on loopback 54330; personal DB is forbidden.'}
 foreach($name in @('CODEF_CONFIG_FILE','CODEF_STORAGE_KEY_FILE','OPENAI_CONFIG_FILE','ACCOUNT_MAIL_CONFIG_FILE')){[Environment]::SetEnvironmentVariable($name,'','Process')}
 $env:RUN_SERVICE_PG_TESTS='true';$env:RUN_FINANCE_PG_TESTS='true';$env:RUN_ACCOUNT_PG_TESTS='true'
 if(!$env:JAVA_HOME){$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'};$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
 $maven=Join-Path $env:USERPROFILE '.m2\wrapper\dists\apache-maven-3.9.5\2021cb71\bin\mvn.cmd';$gradle=Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3\bin\gradle.bat'
 Push-Location (Join-Path $projectRoot 'backend')
 try{$started=Get-Date;Run-Step $maven @('-B','-ntp','-Dtest=*Test,!Android*Test,!Live*Test','test') 'verify-backend.log'
  $totals=@{tests=0;failures=0;errors=0;skipped=0};Get-ChildItem 'target\surefire-reports\TEST-*.xml'|Where-Object LastWriteTime -ge $started|ForEach-Object{$xml=[xml](Get-Content -Raw $_.FullName);foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$xml.testsuite.$key}};if($totals.tests -eq 0 -or $totals.skipped -gt 0){throw 'Expected local tests were not executed.'};$report.backend=$totals
 }finally{Pop-Location}
 Run-Step $gradle @('-p',(Join-Path $projectRoot 'android'),':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:assembleRelease','--console=plain') 'verify-android.log';$report.androidBuild=$true
 if($Emulator){$adb=Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe';if((& $adb -s $Device get-state 2>$null) -ne 'device'){throw 'Start the emulator first.'}
  Run-Step $adb @('-s',$Device,'install','-r',(Join-Path $projectRoot 'android\app\build\outputs\apk\debug\app-debug.apk')) 'verify-install.log';Run-Step $adb @('-s',$Device,'install','-r',(Join-Path $projectRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) 'verify-install-test.log'
  $env:RUN_DEVICE_FREE_ANDROID_TEST='true';$env:RUN_CHECKOUT_ANDROID_PG_TEST='true';$env:RUN_ANDROID_LEDGER_DEVICE_TEST='true';$env:ANDROID_TEST_DEVICE=$Device
  Push-Location (Join-Path $projectRoot 'backend');try{Run-Step $maven @('-B','-ntp','-Dtest=AndroidDeviceFreePostgresTest,AndroidReconciliationPostgresTest,AndroidMerchantMemoryPostgresTest,AndroidCheckoutRecommendationPostgresTest,AndroidLedgerDevicePostgresTest','test') 'verify-emulator.log'}finally{Pop-Location};$report.emulator=$true
 }
 $report.passed=$true
}finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')};$report|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $run 'device-free-verification.json') -Encoding utf8}
$report|ConvertTo-Json -Depth 5
