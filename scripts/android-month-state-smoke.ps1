param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$runId=[guid]::NewGuid().ToString()
$runtime=Join-Path $root '.run'
$evidenceRoot=Join-Path $runtime 'android-month-state'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$runId | Set-Content -LiteralPath (Join-Path $runtime 'android-month-state-current-run.txt')
function AdbCommand([string[]]$Arguments) {
 & $adb -s $Device @Arguments
 if($LASTEXITCODE -ne 0){throw "adb operation failed: $($Arguments[0])"}
}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$instrumentLog=Join-Path $runtime ('android-month-state-'+$runId+'.log')
& $adb -s $Device shell am instrument -w -r -e class dev.cardadvisor.android.MonthlyStateTest -e runId $runId dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner *> $instrumentLog
Get-Content -LiteralPath $instrumentLog -Tail 18
if($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath $instrumentLog -Pattern 'OK \(1 test\)' -Quiet)){throw "UI test failed; run ID $runId. Synthetic test account retained for diagnosis."}
AdbCommand @('pull',"/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$runId",$evidenceRoot)
$email="ui-$runId@example.test"
$sql=@"
SELECT json_build_object('users',(SELECT count(*) FROM app_users WHERE email='$email'),
 'baselineWon',s.untracked_performance_won,'currentQualified',s.current_qualified,
 'baselineSource',s.untracked_source,'baselineDate',s.untracked_as_of,
 'qualificationSource',s.qualification_source,'qualificationDate',s.qualification_as_of,
 'qualificationPeriod',s.qualification_period,'month',s.month,'version',s.version,
 'capWon',s.remaining_cap_won)
FROM card_month_states s JOIN cards c ON c.id=s.card_id AND c.user_id=s.user_id
JOIN app_users u ON u.id=s.user_id WHERE u.email='$email' AND c.payload->>'name'='[가상] B 카드';
"@
Push-Location $root
try {
 $dbJson=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database evidence query failed'}
 $db=$dbJson | ConvertFrom-Json
 $today=(Get-Date).ToString('yyyy-MM-dd')
 $period=(Get-Date -Day 1).AddMonths(-1).ToString('yyyy-MM-dd')
 if($db.users -ne 1 -or $db.baselineWon -ne 340000 -or $db.currentQualified -ne $false -or $db.version -ne 6 -or $db.capWon -ne 10000){throw 'Persisted values/version did not match the Android scenario'}
 if($db.baselineSource -ne 'USER_INPUT' -or $db.qualificationSource -ne 'USER_INPUT' -or $db.baselineDate -ne $today -or $db.qualificationDate -ne $today -or $db.qualificationPeriod -ne $period){throw 'Persisted evidence did not match the Android scenario'}
 $dbJson=$db | ConvertTo-Json -Compress
 $dbJson | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\database.json") -Encoding utf8
 # Deletes only the exact synthetic account created by this run, after evidence has passed.
 $cleanup=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "WITH deleted AS (DELETE FROM app_users WHERE email='$email' RETURNING id) SELECT count(*) FROM deleted;"
 if($LASTEXITCODE -ne 0 -or $cleanup.Trim() -ne '1'){throw 'Synthetic account cleanup failed'}
 $remaining=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM app_users WHERE email='$email';"
 if($LASTEXITCODE -ne 0 -or $remaining.Trim() -ne '0'){throw 'Synthetic account still exists'}
 $result=@{runId=$runId;device=$Device;ui='PASSED';database=$db;syntheticAccountDeleted=$true;actualFinancialIntegration=$false;physicalDevice=($Device -notlike 'emulator-*')}
 $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\verification.json") -Encoding utf8
 $result | ConvertTo-Json -Depth 4 -Compress
} finally { Pop-Location }
AdbCommand @('shell','am','force-stop','dev.cardadvisor.android')
AdbCommand @('shell','am','start','-n','dev.cardadvisor.android/.MainActivity')

