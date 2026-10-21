$ErrorActionPreference='Stop'
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$sql=@'
WITH owners AS (SELECT DISTINCT user_id FROM financial_transactions WHERE source='PROVIDER_REPORTED'), rows AS (
 SELECT e.id,e.purchased_at,e.amount_won,t.id AS evidence,t.merchant_name,t.card_label,t.card_mask,
 CASE WHEN t.id IS NOT NULL THEN COALESCE(c.payload->>'name',t.card_label) ELSE p.payload->>'name' END AS display_card,
 m.card_id AS mapped
 FROM ledger_entries e JOIN owners u ON u.user_id=e.user_id
 LEFT JOIN financial_transactions t ON t.user_id=e.user_id AND t.ledger_id=e.id AND t.kind='APPROVAL'
 LEFT JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'')
 LEFT JOIN cards c ON c.user_id=m.user_id AND c.id=m.card_id
 LEFT JOIN cards p ON p.user_id=e.user_id AND p.id=e.card_id
)
SELECT json_build_object(
 'usersWithProviderData',(SELECT count(*) FROM owners),
 'ledgerRows',count(*),'uniqueLedgerRows',count(DISTINCT id),
 'currentMonthRows',count(*) FILTER(WHERE date_trunc('month',purchased_at)=date_trunc('month',now() AT TIME ZONE 'Asia/Seoul')),
 'financialEvidenceRows',count(evidence),
 'missingMerchant',count(*) FILTER(WHERE evidence IS NOT NULL AND NULLIF(btrim(merchant_name),'') IS NULL),
 'missingDisplayCard',count(*) FILTER(WHERE NULLIF(btrim(display_card),'') IS NULL),
 'missingProviderCardName',count(*) FILTER(WHERE evidence IS NOT NULL AND NULLIF(btrim(card_label),'') IS NULL),
 'confirmedProductRows',count(mapped),
 'missingAmount',count(*) FILTER(WHERE amount_won IS NULL),
 'validMaskedIdentityRows',count(*) FILTER(WHERE card_mask ~ '^\*{13}[0-9]{3}$' OR card_mask ~ '^[0-9]{4}\*{8}[0-9]{4}$'),
 'unlinkedCancellationRows',(SELECT count(*) FROM financial_transactions WHERE source='PROVIDER_REPORTED' AND kind='CANCEL_EVENT' AND status='HELD' AND linked_transaction_id IS NULL)
) FROM rows;
'@
$raw=& $docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose exec -T db psql -U card_advisor -d card_advisor -At -v ON_ERROR_STOP=1 -c $sql
if($LASTEXITCODE -ne 0){throw 'Aggregate metadata audit failed; raw data was not queried'}
$report=$raw|ConvertFrom-Json
$report|ConvertTo-Json|Set-Content -LiteralPath '.run/ledger-real-metadata.json' -Encoding utf8
$report|ConvertTo-Json -Compress
