$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
# Read metadata/booleans only. Never select CID, ciphertext, hashes, credentials or transaction details.
$sql=@'
WITH c AS (SELECT * FROM financial_connections WHERE environment='DEMO'),
connections AS (
 SELECT c.organization,c.state,c.version,c.archived_at IS NOT NULL AS archived,
 CASE WHEN c.last_error ~ '^[A-Z0-9_]{1,80}$' THEN c.last_error ELSE NULL END AS error,
 CASE WHEN c.provider_code ~ '^CF-[0-9]{5}$' THEN c.provider_code ELSE NULL END AS provider_code,
 c.cid_ciphertext IS NOT NULL AS has_encrypted_id,
 COALESCE(c.cid_nonce IS NOT NULL AND octet_length(c.cid_nonce)=12 AND octet_length(c.cid_ciphertext)>=16 AND octet_length(c.cid_hash)=32 AND c.key_id IS NOT NULL,false) AS encryption_shape_valid,
 o.kind AS operation,o.status AS operation_status
 FROM c LEFT JOIN financial_connection_operations o ON o.user_id=c.user_id AND o.connection_id=c.id AND o.id=c.last_operation_id
)
SELECT json_build_object(
 'environment','DEMO',
 'connections',COALESCE((SELECT json_agg(connections) FROM connections),'[]'::json),
 'activeEncryptedConnections',(SELECT count(*) FROM connections WHERE state='ACTIVE' AND operation_status='SUCCEEDED' AND encryption_shape_valid),
 'syncRuns',(SELECT count(*) FROM financial_sync_runs s JOIN c ON c.id=s.connection_id AND c.user_id=s.user_id),
 'processedSyncRuns',(SELECT count(*) FROM financial_sync_runs s JOIN c ON c.id=s.connection_id AND c.user_id=s.user_id WHERE s.status IN('IMPORTED','PARTIAL')),
 'providerReportedRows',(SELECT count(*) FROM financial_transactions t JOIN c ON c.id=t.connection_id AND c.user_id=t.user_id WHERE t.source='PROVIDER_REPORTED'),
 'syntheticRows',(SELECT count(*) FROM financial_transactions t JOIN c ON c.id=t.connection_id AND c.user_id=t.user_id WHERE t.source='SYNTHETIC'),
 'plaintextCredentialColumns',(SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN('financial_connections','financial_connection_operations') AND column_name IN('password','login_id','connected_id','client_secret'))
);
'@
Push-Location $root
try{
 $raw=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
 if($LASTEXITCODE -ne 0){throw 'Live connection metadata query failed'}
 $report=$raw | ConvertFrom-Json
 $report | Add-Member -NotePropertyName actualConnectionVerified -NotePropertyValue ($report.activeEncryptedConnections -gt 0)
 $report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $root '.run/codef-live-state.json') -Encoding utf8
 $report | ConvertTo-Json -Depth 5 -Compress
}finally{Pop-Location}
