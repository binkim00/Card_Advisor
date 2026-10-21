param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$runId=[guid]::NewGuid().ToString()
$runtime=Join-Path $root '.run'
$evidenceRoot=Join-Path $runtime 'android-fixed'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$runId | Set-Content -LiteralPath (Join-Path $runtime 'android-fixed-current-run.txt')
function AdbCommand([string[]]$Arguments) {
 & $adb -s $Device @Arguments
 if($LASTEXITCODE -ne 0){throw "adb operation failed: $($Arguments[0])"}
}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$instrumentLog=Join-Path $runtime ('android-fixed-'+$runId+'.log')
& $adb -s $Device shell am instrument -w -r -e class dev.cardadvisor.android.FixedCostFlowTest -e runId $runId dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner *> $instrumentLog
Get-Content -LiteralPath $instrumentLog -Tail 18
if($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath $instrumentLog -Pattern 'OK \(1 test\)' -Quiet)){ & $adb -s $Device pull "/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$runId" $evidenceRoot; throw "UI test failed; run ID $runId. Synthetic test account retained for diagnosis."}
AdbCommand @('pull',"/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$runId",$evidenceRoot)
$email="ui-$runId@example.test"
$sql=@"
WITH u AS (SELECT id FROM app_users WHERE email='$email')
SELECT json_build_object(
 'users',(SELECT count(*) FROM u),
 'plans',(SELECT json_agg(json_build_object('id',id,'version',version)) FROM fixed_cost_plans WHERE user_id IN (SELECT id FROM u)),
 'rules',(SELECT json_agg(json_build_object('month',effective_month,'amountWon',amount_won,'day',day_of_month,'active',active) ORDER BY effective_month) FROM fixed_cost_rules WHERE user_id IN (SELECT id FROM u)),
 'links',(SELECT count(*) FROM fixed_cost_links WHERE user_id IN (SELECT id FROM u)),
 'budgets',(SELECT json_agg(json_build_object('month',month,'payload',payload,'version',version) ORDER BY month) FROM budgets WHERE user_id IN (SELECT id FROM u)),
 'netWon',(SELECT sum(e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.transaction_id=e.id AND c.user_id=e.user_id),0)) FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'netBenefitWon',(SELECT sum(floor((e.card_charge_won-COALESCE((SELECT sum(c.card_refund_won) FROM cancellations c WHERE c.transaction_id=e.id AND c.user_id=e.user_id),0))*e.benefit_rate_bp/10000))::bigint FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'latestRecommendation',(SELECT payload FROM recommendations WHERE user_id IN (SELECT id FROM u) ORDER BY expires_at DESC LIMIT 1),
 'migration',(SELECT max(version::integer) FROM flyway_schema_history WHERE success=true));
"@

Push-Location $root
try {
 $dbJson=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database evidence query failed'}
 $db=$dbJson | ConvertFrom-Json
 if($db.users -ne 1 -or $db.plans.Count -ne 1 -or $db.plans[0].version -ne 10 -or $db.rules.Count -ne 2 -or $db.links -ne 1 -or $db.netWon -ne 30000 -or $db.netBenefitWon -ne 300 -or $db.migration -ne 8){throw 'Fixed cost DB evidence mismatch'}
 if($db.rules[0].amountWon -ne 60000 -or $db.rules[1].amountWon -ne 80000 -or !$db.rules[0].active -or !$db.rules[1].active -or $db.rules[0].day -ne 31){throw 'Month-specific rule evidence mismatch'}
 if($db.budgets.Count -ne 2 -or $db.budgets[0].payload.fixedCostMode -ne 'SCHEDULED' -or $db.budgets[1].payload.fixedCostMode -ne 'SCHEDULED' -or $db.budgets[0].payload.fixedRemainingWon -ne 30000 -or $db.budgets[1].payload.fixedRemainingWon -ne 0){throw 'Manual/scheduled source preservation mismatch'}
 if($db.latestRecommendation.overallAfterWon -ne 40000 -or $db.latestRecommendation.availableAfterWon -ne 10000){throw 'Reservation recommendation mismatch'}
 $db | Add-Member -NotePropertyName calculatedCurrentReservationWon -NotePropertyValue ($db.rules[0].amountWon-$db.netWon)
 $db | Add-Member -NotePropertyName calculatedNextReservationWon -NotePropertyValue $db.rules[1].amountWon
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
