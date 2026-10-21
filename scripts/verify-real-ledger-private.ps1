$ErrorActionPreference='Stop'
$adb='C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
function DataSnapshot {
 $queries=@('app_users','cards','card_month_states','budgets','ledger_entries','cancellations','financial_transactions','financial_card_mappings','financial_connections','financial_sync_runs') | ForEach-Object {"SELECT '$_' AS name,md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) AS digest FROM $_ t"}
 $sql='SELECT json_object_agg(name,digest) FROM ('+($queries -join ' UNION ALL ')+') s'
 $result=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Private integrity snapshot failed; contents withheld'}
 return ($result -join '')
}
& $adb -s R3CX701TE5J shell am force-stop dev.cardadvisor.android
& $adb -s R3CX701TE5J reverse tcp:18080 tcp:18080 | Out-Null
$before=DataSnapshot
$report=[ordered]@{passed=$false;actualAccount=$true;physicalDevice=$true;screenshotsCaptured=$false;rawValuesExported=$false}
try{
 $raw=& $adb -s R3CX701TE5J shell am instrument -w -r -e class dev.cardadvisor.android.PrivateLedgerAuditTest#readOnlyActualAccountLedger dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner
 $report.passed=($raw -join "`n").Contains('OK (1 test)')
 $report.steps=@($raw | Where-Object {$_ -match '^INSTRUMENTATION_STATUS: auditStep=[a-z_]+$'} | ForEach-Object {$_.Split('=')[1]})
 foreach($line in $raw){if($line -match '^INSTRUMENTATION_STATUS: audit_([a-z_]+)=([0-9]+)$'){$report[$matches[1]]=[int]$matches[2]}}
 $report.failureLocations=@($raw | Where-Object {$_ -match 'PrivateLedgerAuditTest\.kt:[0-9]+'} | ForEach-Object {if($_ -match 'PrivateLedgerAuditTest\.kt:([0-9]+)'){'PrivateLedgerAuditTest.kt:'+$matches[1]}} | Select-Object -Unique)
 $raw=$null
}finally{
 $report.existingDataUnchanged=($before -eq (DataSnapshot))
 $before=$null
 $report|ConvertTo-Json -Depth 4|Set-Content -LiteralPath '.run/ledger-real-private-audit.json' -Encoding utf8
 & $adb -s R3CX701TE5J shell am force-stop dev.cardadvisor.android
 & $adb -s R3CX701TE5J reverse tcp:18080 tcp:18080 | Out-Null
 & $adb -s R3CX701TE5J shell am start -W -n dev.cardadvisor.android/.MainActivity | Out-Null
}
$report|ConvertTo-Json -Depth 4 -Compress
if(!$report.passed -or !$report.existingDataUnchanged){throw 'Private audit needs follow-up; see sanitized report only'}
