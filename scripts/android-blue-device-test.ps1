param([string]$Device='R3CX701TE5J',[Parameter(Mandatory=$true)][string]$AppApk,[Parameter(Mandatory=$true)][string]$TestApk,[switch]$Stress)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$runId=[guid]::NewGuid().ToString()
$runtime=Join-Path $root '.run'
$evidenceRoot=Join-Path $runtime 'blue-device'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$runId | Set-Content -LiteralPath (Join-Path $runtime 'blue-device-current-run.txt')
function AdbCommand([string[]]$Arguments) {
 & $adb -s $Device @Arguments
 if($LASTEXITCODE -ne 0){throw "adb operation failed: $($Arguments[0])"}
}
$activeInstrumentation=(& $adb -s $Device shell dumpsys activity | Out-String)
if($LASTEXITCODE -ne 0 -or $activeInstrumentation -match 'ActiveInstrumentation\{'){throw 'Device unavailable or another instrumentation run is active'}
Invoke-RestMethod 'http://127.0.0.1:18080/v1/health' -TimeoutSec 5 | Out-Null
$deviceModel=(& $adb -s $Device shell getprop ro.product.model).Trim()
$androidVersion=(& $adb -s $Device shell getprop ro.build.version.release).Trim()
$physicalDevice=((& $adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1')
AdbCommand @('reverse','tcp:18080','tcp:18080')
AdbCommand @('install','-r',$AppApk)
AdbCommand @('install','-r',$TestApk)
$instrumentArguments=@('shell','am','instrument','-w','-r','-e','class','dev.cardadvisor.android.BlueDeviceFlowTest','-e','runId',$runId)
if($Stress){$instrumentArguments+=@('-e','stress','true')}
$instrumentArguments+='dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner'
$instrumentOutput=(& $adb -s $Device @instrumentArguments 2>&1 | Out-String)
$instrumentPassed=$LASTEXITCODE -eq 0 -and $instrumentOutput.Contains('OK (1 test)')
$locations=[regex]::Matches($instrumentOutput,'BlueDeviceFlowTest\.kt:\d+') | ForEach-Object {$_.Value} | Select-Object -Unique
$failureTypes=[regex]::Matches($instrumentOutput,'(?:java|androidx|android)\.[A-Za-z0-9_.$]+(?:Exception|Error)\b') | ForEach-Object {$_.Value} | Select-Object -Unique
@{runId=$runId;passed=$instrumentPassed;failureLocations=@($locations);failureTypes=@($failureTypes)} | ConvertTo-Json | Set-Content (Join-Path $runtime "blue-device-$runId-result.json") -Encoding utf8
# Never save raw instrumentation output, which can contain input values in assertion messages.
$instrumentOutput=$null
if(!$instrumentPassed){throw "UI test failed; run ID $runId. Synthetic test account retained for diagnosis."}
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
 if($db.users -ne 1 -or $db.cards -ne 2 -or $db.purchases -ne 1 -or $db.cancellations -ne 1 -or $db.purchaseWon -ne 50000 -or $db.cancelWon -ne 20000 -or $db.netWon -ne 30000 -or $db.latestRecommendationBudgetWon -ne 40000){throw 'Database evidence did not match the app scenario'}
 if($db.linkedCardName -ne '[가상] B 카드' -or $db.cardChargeWon -ne 50000 -or $db.cardRefundWon -ne 20000 -or $db.baselineWon -ne 270000 -or $db.currentQualified -ne $true){throw 'Card-linked performance evidence did not match'}
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
 $result=@{stressCycles=$(if($Stress){12}else{0});runId=$runId;device=$Device;model=$deviceModel;androidVersion=$androidVersion;ui='PASSED';database=$db;syntheticAccountDeleted=$true;actualFinancialIntegration=$false;physicalDevice=$physicalDevice;apkSha256=(Get-FileHash $AppApk -Algorithm SHA256).Hash.ToLowerInvariant()}
 $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceRoot "$runId\verification.json") -Encoding utf8
 $result | ConvertTo-Json -Depth 4 -Compress
} finally { Pop-Location }
AdbCommand @('shell','am','force-stop','dev.cardadvisor.android')
AdbCommand @('shell','am','start','-n','dev.cardadvisor.android/.MainActivity')
