param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$runId=[guid]::NewGuid().ToString()
$runtime=Join-Path $root '.run'
$evidenceRoot=Join-Path $runtime 'android-budget'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$runId | Set-Content -LiteralPath (Join-Path $runtime 'android-budget-current-run.txt')
function AdbCommand([string[]]$Arguments) {
 & $adb -s $Device @Arguments
 if($LASTEXITCODE -ne 0){throw "adb operation failed: $($Arguments[0])"}
}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$instrumentLog=Join-Path $runtime ('android-budget-'+$runId+'.log')
& $adb -s $Device shell am instrument -w -r -e class dev.cardadvisor.android.BudgetFlowTest -e runId $runId dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner *> $instrumentLog
Get-Content -LiteralPath $instrumentLog -Tail 18
if($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath $instrumentLog -Pattern 'OK \(1 test\)' -Quiet)){throw "UI test failed; run ID $runId. Synthetic test account retained for diagnosis."}
AdbCommand @('pull',"/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$runId",$evidenceRoot)
$email="ui-$runId@example.test"
$sql=@"
WITH u AS (SELECT id FROM app_users WHERE email='$email')
SELECT json_build_object(
 'users',(SELECT count(*) FROM u),
 'budgets',(SELECT json_agg(json_build_object('month',month,'payload',payload,'version',version) ORDER BY month) FROM budgets WHERE user_id IN (SELECT id FROM u)),
 'netWon',(SELECT sum(e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.transaction_id=e.id AND c.user_id=e.user_id),0)) FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'baselineWon',(SELECT s.untracked_performance_won FROM card_month_states s JOIN cards p ON p.id=s.card_id AND p.user_id=s.user_id WHERE s.user_id IN (SELECT id FROM u) AND p.payload->>'name'='[가상] B 카드' LIMIT 1),
 'baseCapWon',(SELECT s.remaining_cap_won FROM card_month_states s JOIN cards p ON p.id=s.card_id AND p.user_id=s.user_id WHERE s.user_id IN (SELECT id FROM u) AND p.payload->>'name'='[가상] B 카드' LIMIT 1),
 'netBenefitWon',(SELECT sum(floor((e.card_charge_won-COALESCE((SELECT sum(c.card_refund_won) FROM cancellations c WHERE c.transaction_id=e.id AND c.user_id=e.user_id),0))*e.benefit_rate_bp/10000))::bigint FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'latestRecommendation',(SELECT payload FROM recommendations WHERE user_id IN (SELECT id FROM u) ORDER BY expires_at DESC LIMIT 1));
"@

Push-Location $root
try {
 $dbJson=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database evidence query failed'}
 $db=$dbJson | ConvertFrom-Json
 $current=$db.budgets[0];$next=$db.budgets[1]
 if($db.users -ne 1 -or $db.budgets.Count -ne 2 -or $db.netWon -ne 30000){throw 'Budget database evidence mismatch'}
 if($current.version -ne 4 -or $current.payload.totalWon -ne 240000 -or $current.payload.fixedRemainingWon -ne 20000 -or $current.payload.categories.SHOPPING -ne 100000 -or $current.payload.categories.FOOD -ne 80000){throw 'Current month persisted values mismatch'}
 if($next.version -ne 1 -or $next.payload.totalWon -ne 50000 -or $next.payload.fixedRemainingWon -ne 0 -or $next.payload.categories.FOOD -ne 20000 -or $next.payload.categories.OTHER -ne 0 -or $next.payload.categories.PSObject.Properties.Name -contains 'SHOPPING'){throw 'New month/unset vs zero mismatch'}
 if($db.baselineWon -ne 270000 -or $db.baseCapWon -ne 10000 -or $db.netBenefitWon -ne 300){throw 'Card calculation independence mismatch'}
 if($db.latestRecommendation.overallAfterWon -ne 160000 -or $db.latestRecommendation.categoryAfterWon -ne 20000 -or $db.latestRecommendation.availableAfterWon -ne 140000){throw 'Updated recommendation mismatch'}
 $dbJson=$db | ConvertTo-Json -Depth 12 -Compress
 $dbJson | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\database.json") -Encoding utf8
 # Deletes only the exact synthetic account created by this run, after evidence has passed.
 $cleanup=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "WITH deleted AS (DELETE FROM app_users WHERE email='$email' RETURNING id) SELECT count(*) FROM deleted;"
 if($LASTEXITCODE -ne 0 -or $cleanup.Trim() -ne '1'){throw 'Synthetic account cleanup failed'}
 $remaining=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM app_users WHERE email='$email';"
 if($LASTEXITCODE -ne 0 -or $remaining.Trim() -ne '0'){throw 'Synthetic account still exists'}
 $result=@{runId=$runId;device=$Device;ui='PASSED';database=$db;syntheticAccountDeleted=$true;actualFinancialIntegration=$false;physicalDevice=$false}
 $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\verification.json") -Encoding utf8
 $result | ConvertTo-Json -Depth 12 -Compress
} finally { Pop-Location }
AdbCommand @('shell','am','force-stop','dev.cardadvisor.android')
AdbCommand @('shell','am','start','-n','dev.cardadvisor.android/.MainActivity')
