param([string]$Device='R3CX701TE5J',[switch]$SkipInstall)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$run=[guid]::NewGuid().ToString()
$email="service-ui-$run@example.test"
$dir=Join-Path $root ".run/service-device/$run"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$run | Set-Content (Join-Path $root '.run/service-device-current-run.txt')
function Adb([string[]]$a){& $adb -s $Device @a;if($LASTEXITCODE -ne 0){throw 'Device operation failed'}}
function Query([string]$sql){
 $out=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Database verification failed'}
 return ($out | Out-String).Trim()
}
function Instrument([string]$method,[int]$tests=1){
 $raw=(& $adb -s $Device shell am instrument -w -r -e class $method -e runId $run dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String)
 $passed=$LASTEXITCODE -eq 0 -and $raw.Contains("OK ($tests test")
 $result=@{passed=$passed;test=$method;locations=@([regex]::Matches($raw,'(?:ServiceCompletionFlowTest|CheckoutDetectionTest)\.kt:\d+')|ForEach-Object{$_.Value}|Select-Object -Unique);types=@([regex]::Matches($raw,'(?:java|androidx|android)\.[A-Za-z0-9_.$]+(?:Exception|Error)\b')|ForEach-Object{$_.Value}|Select-Object -Unique)}
 $result['reasonCodes']=@()
 if($raw -match '(?i)none of the parents are scrollable'){$result['reasonCodes']+='NO_SCROLL_PARENT'}
 if($raw -match '(?i)condition still not satisfied'){$result['reasonCodes']+='WAIT_TIMEOUT'}
 if($raw -match '(?i)exactly.*node.*found.*[02-9]'){$result['reasonCodes']+='NODE_MATCH_COUNT'}
 $raw=$null
 $result | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $dir ($method.Split('#')[-1]+'.json')) -Encoding utf8
 if(!$passed){throw "Device test failed; inspect sanitized evidence $dir"}
}
Push-Location $root
try{
 $active=(& $adb -s $Device shell dumpsys activity | Out-String)
 if($LASTEXITCODE -ne 0 -or $active -match 'ActiveInstrumentation\{'){throw 'Device unavailable or in use'}
 Invoke-RestMethod http://127.0.0.1:18080/v1/health -TimeoutSec 5 | Out-Null
 Adb @('reverse','tcp:18080','tcp:18080')
 if(!$SkipInstall){Adb @('install','-r',(Join-Path $root 'android/app/build/outputs/apk/debug/app-debug.apk'));Adb @('install','-r',(Join-Path $root 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))}
 Instrument 'dev.cardadvisor.android.CheckoutDetectionTest'
 Instrument 'dev.cardadvisor.android.ServiceCompletionFlowTest#actualCardsConsentPurchaseRefundPersistence'
 Adb @('pull',"/sdcard/Android/data/dev.cardadvisor.android/files/e2e/$run",(Join-Path $root '.run/service-device'))
 $db=Query @"
WITH u AS (SELECT id FROM app_users WHERE email='$email')
SELECT json_build_object('users',(SELECT count(*) FROM u),'cards',(SELECT count(*) FROM cards WHERE user_id IN(SELECT id FROM u)),
'purchaseWon',(SELECT sum(amount_won) FROM ledger_entries WHERE user_id IN(SELECT id FROM u)),
'cancelWon',(SELECT sum(amount_won) FROM cancellations WHERE user_id IN(SELECT id FROM u)),
'facts',(SELECT purchase_facts->>'merchant' FROM ledger_entries WHERE user_id IN(SELECT id FROM u) LIMIT 1),
'rule',(SELECT benefit_rule_version FROM ledger_entries WHERE user_id IN(SELECT id FROM u) LIMIT 1),
'cancelDated',(SELECT bool_and(received_at IS NOT NULL) FROM cancellations WHERE user_id IN(SELECT id FROM u)),
'aiConsent',(SELECT accepted FROM user_consents WHERE user_id IN(SELECT id FROM u) AND purpose='AI'),
'consentEvents',(SELECT count(*) FROM consent_events WHERE user_id IN(SELECT id FROM u)),
'latestBudgetAfter',(SELECT (payload->>'overallAfterWon')::bigint FROM recommendations WHERE user_id IN(SELECT id FROM u) ORDER BY expires_at DESC LIMIT 1));
"@ | ConvertFrom-Json
 if($db.users -ne 1 -or $db.cards -ne 3 -or $db.purchaseWon -ne 50000 -or $db.cancelWon -ne 20000 -or $db.facts -ne 'NAVER_PLUS_STORE' -or $db.rule -ne 'OFFICIAL_HANA_TRAVELOG_20240308' -or !$db.cancelDated -or $db.aiConsent -ne $false -or $db.consentEvents -ne 3 -or $db.latestBudgetAfter -ne 40000){throw 'Device flow and PostgreSQL differ'}
 $db | ConvertTo-Json | Set-Content (Join-Path $dir 'database.json') -Encoding utf8
 Instrument 'dev.cardadvisor.android.ServiceCompletionFlowTest#deleteAccount'
 if((Query "SELECT count(*) FROM app_users WHERE email='$email';") -ne '0'){throw 'Account deletion did not cascade'}
 @{runId=$run;device=$Device;physicalDevice=((& $adb -s $Device shell getprop ro.kernel.qemu | Out-String).Trim() -ne '1');ui='PASSED';database='PASSED';deletion='PASSED';actualFinancialCalls=$false;paidAiCalls=$false;realCheckoutRouteVerified=$false;apkSha256=(Get-FileHash (Join-Path $root 'android/app/build/outputs/apk/debug/app-debug.apk')).Hash.ToLowerInvariant()} | ConvertTo-Json | Set-Content (Join-Path $dir 'verification.json') -Encoding utf8
 Get-Content (Join-Path $dir 'verification.json')
}finally{Pop-Location}
