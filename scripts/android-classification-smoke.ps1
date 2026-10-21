param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$runId=[guid]::NewGuid().ToString()
$runtime=Join-Path $root '.run'
$evidenceRoot=Join-Path $runtime 'android-classification'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$runId | Set-Content -LiteralPath (Join-Path $runtime 'android-classification-current-run.txt')
function AdbCommand([string[]]$Arguments) {
 & $adb -s $Device @Arguments
 if($LASTEXITCODE -ne 0){throw "adb operation failed: $($Arguments[0])"}
}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbCommand @('install','-r',(Join-Path $root 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$instrumentLog=Join-Path $runtime ('android-classification-'+$runId+'.log')
& $adb -s $Device shell am instrument -w -r -e class dev.cardadvisor.android.ClassificationFlowTest -e runId $runId dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner *> $instrumentLog
Get-Content -LiteralPath $instrumentLog -Tail 18
if($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath $instrumentLog -Pattern 'OK \(1 test\)' -Quiet)){throw "UI test failed; run ID $runId. Synthetic test account retained for diagnosis."}
AdbCommand @('pull',"/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$runId",$evidenceRoot)
$email="ui-$runId@example.test"
$sql=@"
WITH u AS (SELECT id FROM app_users WHERE email='$email'),
e AS (SELECT amount_won FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
c AS (SELECT amount_won FROM cancellations WHERE user_id IN (SELECT id FROM u))
SELECT json_build_object(
 'users',(SELECT count(*) FROM u),
 'cards',(SELECT count(*) FROM cards WHERE user_id IN (SELECT id FROM u)),
 'purchases',(SELECT count(*) FROM e),
 'cancellations',(SELECT count(*) FROM c),
 'purchaseWon',(SELECT sum(amount_won) FROM e),
 'cancelWon',(SELECT sum(amount_won) FROM c),
 'netWon',(SELECT sum(amount_won) FROM e)-(SELECT sum(amount_won) FROM c),
 'linkedCardName',(SELECT p.payload->>'name' FROM cards p JOIN ledger_entries l ON l.card_id=p.id AND l.user_id=p.user_id WHERE l.user_id IN (SELECT id FROM u) LIMIT 1),
 'cardChargeWon',(SELECT sum(card_charge_won) FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'cardRefundWon',(SELECT sum(card_refund_won) FROM cancellations WHERE user_id IN (SELECT id FROM u)),
 'category',(SELECT category FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'included',(SELECT included FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'classificationVersion',(SELECT classification_version FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'originalCategory',(SELECT original_category FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'originalIncluded',(SELECT original_included FROM ledger_entries WHERE user_id IN (SELECT id FROM u)),
 'budgetSpentWon',(SELECT COALESCE(sum(CASE WHEN e.included THEN e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) ELSE 0 END),0) FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'baseCapWon',(SELECT s.remaining_cap_won FROM card_month_states s JOIN cards p ON p.id=s.card_id AND p.user_id=s.user_id WHERE s.user_id IN (SELECT id FROM u) AND p.payload->>'name'='[가상] B 카드' LIMIT 1),
 'benefitRule',(SELECT benefit_rule_version FROM ledger_entries WHERE user_id IN (SELECT id FROM u) LIMIT 1),
 'netBenefitWon',(SELECT sum(floor((e.card_charge_won-COALESCE((SELECT sum(c.card_refund_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0))*e.benefit_rate_bp/10000))::bigint FROM ledger_entries e WHERE user_id IN (SELECT id FROM u)),
 'baselineWon',(SELECT s.untracked_performance_won FROM card_month_states s JOIN cards p ON p.id=s.card_id AND p.user_id=s.user_id WHERE s.user_id IN (SELECT id FROM u) AND p.payload->>'name'='[가상] B 카드' LIMIT 1),
 'currentQualified',(SELECT s.current_qualified FROM card_month_states s JOIN cards p ON p.id=s.card_id AND p.user_id=s.user_id WHERE s.user_id IN (SELECT id FROM u) AND p.payload->>'name'='[가상] B 카드' LIMIT 1),
 'latestRecommendationBudgetWon',(SELECT (payload->>'overallAfterWon')::bigint FROM recommendations WHERE user_id IN (SELECT id FROM u) ORDER BY expires_at DESC LIMIT 1));
"@
Push-Location $root
try {
 $dbJson=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database evidence query failed'}
 $db=$dbJson | ConvertFrom-Json
 if($db.users -ne 1 -or $db.cards -ne 2 -or $db.purchases -ne 1 -or $db.cancellations -ne 1 -or $db.purchaseWon -ne 50000 -or $db.cancelWon -ne 20000 -or $db.netWon -ne 30000 -or $db.latestRecommendationBudgetWon -ne 70000){throw 'Database evidence did not match the app scenario'}
 if($db.linkedCardName -ne '[가상] B 카드' -or $db.cardChargeWon -ne 50000 -or $db.cardRefundWon -ne 20000 -or $db.baselineWon -ne 270000 -or $db.currentQualified -ne $true){throw 'Card-linked performance evidence did not match'}
 if($db.category -ne 'FOOD' -or $db.included -ne $false -or $db.classificationVersion -ne 5 -or $db.originalCategory -ne 'SHOPPING' -or $db.originalIncluded -ne $true -or $db.budgetSpentWon -ne 0){throw 'Classification and independent budget evidence did not match'}
 if($db.baseCapWon -ne 10000 -or $db.netBenefitWon -ne 300 -or $db.benefitRule -ne 'SYNTHETIC_BENEFIT_V1'){throw 'Benefit snapshot/base pool evidence did not match'}
 $db | Add-Member -NotePropertyName calculatedRemainingCapWon -NotePropertyValue ($db.baseCapWon-$db.netBenefitWon)
 $db | Add-Member -NotePropertyName calculatedPerformanceWon -NotePropertyValue ($db.baselineWon+$db.cardChargeWon-$db.cardRefundWon)
 $dbJson=$db | ConvertTo-Json -Compress
 $dbJson | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\database.json") -Encoding utf8
 # Deletes only the exact synthetic account created by this run, after evidence has passed.
 $cleanup=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "WITH deleted AS (DELETE FROM app_users WHERE email='$email' RETURNING id) SELECT count(*) FROM deleted;"
 if($LASTEXITCODE -ne 0 -or $cleanup.Trim() -ne '1'){throw 'Synthetic account cleanup failed'}
 $remaining=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM app_users WHERE email='$email';"
 if($LASTEXITCODE -ne 0 -or $remaining.Trim() -ne '0'){throw 'Synthetic account still exists'}
 $result=@{runId=$runId;device=$Device;ui='PASSED';database=$db;syntheticAccountDeleted=$true;actualFinancialIntegration=$false;physicalDevice=$false}
 $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\verification.json") -Encoding utf8
 $result | ConvertTo-Json -Depth 4 -Compress
} finally { Pop-Location }
AdbCommand @('shell','am','force-stop','dev.cardadvisor.android')
AdbCommand @('shell','am','start','-n','dev.cardadvisor.android/.MainActivity')
