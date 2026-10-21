param([string]$Device='emulator-5554',[string]$Phase='verify',[switch]$AllowFailure)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
if($Phase -notmatch '^[a-z-]+$'){throw 'Invalid phase'}
$runId=[guid]::NewGuid().ToString()
$evidenceRoot=Join-Path $root '.run\lifecycle'
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
$runId | Set-Content (Join-Path $root ".run\lifecycle-$Phase-run.txt")
function AdbCommand([string[]]$Arguments){& $adb -s $Device @Arguments;if($LASTEXITCODE -ne 0){throw 'ADB operation failed'}}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$log=Join-Path $root ".run\lifecycle-$Phase-$runId.log"
& $adb -s $Device shell am instrument -w -r -e class dev.cardadvisor.android.FixedCostLifecycleTest -e runId $runId dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner *> $log
$testExit=$LASTEXITCODE
& $adb -s $Device pull "/sdcard/Android/data/dev.cardadvisor.android/files/lifecycle/$runId" $evidenceRoot
Get-Content -LiteralPath $log -Tail 26
$passed=$testExit -eq 0 -and (Select-String -LiteralPath $log -Pattern 'OK \(2 tests\)' -Quiet)
if(!$passed){
 if($AllowFailure){Write-Output "EXPECTED DIAGNOSTIC RESULT: NOT_PASSED $runId";return}
 throw "Lifecycle test failed; retained synthetic account lifecycle-$runId@example.test"
}
$navigation=Get-Content (Join-Path $evidenceRoot "$runId\navigation.json") | ConvertFrom-Json
$cancellation=Get-Content (Join-Path $evidenceRoot "$runId\cancellation.json") | ConvertFrom-Json
if($navigation.completedCycles -ne 12 -or $cancellation.lateCallbacks -ne 0){throw 'Lifecycle evidence mismatch'}
$email="lifecycle-$runId@example.test"
Push-Location $root
try {
 $sql="WITH u AS (SELECT id FROM app_users WHERE email='$email') SELECT json_build_object('users',(SELECT count(*) FROM u),'plans',(SELECT count(*) FROM fixed_cost_plans WHERE user_id IN (SELECT id FROM u)),'amountWon',(SELECT amount_won FROM fixed_cost_rules WHERE user_id IN (SELECT id FROM u)),'ledger',(SELECT count(*) FROM ledger_entries WHERE user_id IN (SELECT id FROM u)));"
 $raw=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database query failed'}
 $db=$raw | ConvertFrom-Json
 if($db.users -ne 1 -or $db.plans -ne 1 -or $db.amountWon -ne 30000 -or $db.ledger -ne 0){throw 'Navigation changed saved values unexpectedly'}
 $db | ConvertTo-Json | Set-Content (Join-Path $evidenceRoot "$runId\database.json") -Encoding utf8
 $deleted=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "WITH d AS (DELETE FROM app_users WHERE email='$email' RETURNING id) SELECT count(*) FROM d;"
 if($LASTEXITCODE -ne 0 -or $deleted.Trim() -ne '1'){throw 'Synthetic account cleanup failed'}
 $left=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM app_users WHERE email='$email';"
 if($LASTEXITCODE -ne 0 -or $left.Trim() -ne '0'){throw 'Synthetic account cleanup not confirmed'}
 $result=@{runId=$runId;device=$Device;status='PASSED';navigation=$navigation;cancellation=$cancellation;database=$db;syntheticAccountDeleted=$true;physicalDevice=$false}
 $result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $evidenceRoot "$runId\verification.json") -Encoding utf8
 $result | ConvertTo-Json -Depth 6 -Compress
} finally {Pop-Location}
