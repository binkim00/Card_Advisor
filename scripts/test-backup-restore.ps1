$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe';$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
$fixture='card_advisor_backup_test_'+[Guid]::NewGuid().ToString('N');$id=[Guid]::NewGuid().ToString()
function Sql([string]$db,[string]$sql){& $docker exec card-advisor-db-1 psql -U card_advisor -d $db -v ON_ERROR_STOP=1 -Atc $sql *> $null;if($LASTEXITCODE -ne 0){throw 'Synthetic backup SQL failed.'}}
try{
 Sql 'postgres' ('CREATE DATABASE '+$fixture)
 Sql $fixture 'CREATE TABLE app_users(id uuid PRIMARY KEY);CREATE TABLE deleted_user_tombstones(user_id uuid PRIMARY KEY,deleted_at timestamptz NOT NULL DEFAULT now())'
 foreach($table in @('cards','ledger_entries','cancellations','financial_transactions','financial_card_mappings','financial_performance_reviews','financial_benefit_reviews')){Sql $fixture ('CREATE TABLE '+$table+'(user_id uuid REFERENCES app_users(id) ON DELETE CASCADE)')}
 Sql $fixture ("INSERT INTO app_users VALUES ('"+$id+"');INSERT INTO ledger_entries VALUES ('"+$id+"')")
 $first=(& (Join-Path $PSScriptRoot 'backup-local.ps1') -TestDatabase $fixture -VerifyRestore)|ConvertFrom-Json
 if(!$first.restoreVerified){throw 'Fresh restore was not verified.'}
 Sql $fixture ("INSERT INTO deleted_user_tombstones(user_id) VALUES ('"+$id+"');DELETE FROM app_users WHERE id='"+$id+"'")
 $second=(& (Join-Path $PSScriptRoot 'backup-local.ps1') -TestDatabase $fixture -RestoreFrom (Join-Path $projectRoot ('.local/backup-tests/'+$first.backupFile)))|ConvertFrom-Json
 if(!$second.restoreVerified -or !$second.deletedUsersFiltered -or $second.deletionEntriesApplied -ne 1){throw 'Older backup deletion filtering failed.'}
 @{passed=$true;syntheticOnly=$true;freshRestore=$true;olderBackupDeletedAccountExcluded=$true;originalDatabaseUntouched=$true}|ConvertTo-Json|Tee-Object -FilePath (Join-Path $projectRoot '.run/backup-deletion-test.json')
}finally{if($fixture -notmatch '^card_advisor_backup_test_[a-f0-9]{32}$'){throw 'Unsafe synthetic cleanup target.'};Sql 'postgres' ('DROP DATABASE IF EXISTS '+$fixture)}
