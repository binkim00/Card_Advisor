param([switch]$VerifyRestore,[string]$RestoreFrom,[string]$TestDatabase)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$backupRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot '.local\backups'))
if(!$backupRoot.StartsWith([IO.Path]::GetFullPath($projectRoot)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Backup path is outside project.'}
$docker='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
$container='card-advisor-db-1'
$sourceDatabase='card_advisor'
if($TestDatabase){if($TestDatabase -notmatch '^card_advisor_backup_test_[a-f0-9]{32}$'){throw 'Invalid synthetic backup database.'};$sourceDatabase=$TestDatabase;$backupRoot=Join-Path $projectRoot '.local\backup-tests'}
$owner=[Security.Principal.WindowsIdentity]::GetCurrent().Name
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
& icacls.exe $backupRoot /inheritance:r /grant:r "${owner}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not protect backup directory.'}
function Invoke-DatabaseBytes([string[]]$Arguments,[byte[]]$InputBytes){
 $start=[Diagnostics.ProcessStartInfo]::new($docker);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardInput=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 foreach($argument in $Arguments){$start.ArgumentList.Add($argument)}
 $process=[Diagnostics.Process]::new();$process.StartInfo=$start;[void]$process.Start();$errorRead=$process.StandardError.ReadToEndAsync();$output=[IO.MemoryStream]::new();$copy=$process.StandardOutput.BaseStream.CopyToAsync($output)
 try{if($InputBytes){$process.StandardInput.BaseStream.Write($InputBytes,0,$InputBytes.Length)};$process.StandardInput.Close();if(!$process.WaitForExit(120000)){$process.Kill($true);throw 'Database utility timed out; details withheld.'};$null=$copy.GetAwaiter().GetResult();$null=$errorRead.GetAwaiter().GetResult();if($process.ExitCode -ne 0){throw 'Database utility failed; private output withheld.'};return ,$output.ToArray()}finally{$output.Dispose();$process.Dispose()}
}
function Database-Text([string]$Database,[string]$Sql){$bytes=Invoke-DatabaseBytes @('exec',$container,'psql','-U','card_advisor','-d',$Database,'-v','ON_ERROR_STOP=1','-Atc',$Sql) $null;try{return [Text.Encoding]::UTF8.GetString($bytes).Trim()}finally{[Array]::Clear($bytes,0,$bytes.Length)}}
$plain=$null;$recovered=$null;$created=$false;$restoreDatabase='card_advisor_restore_'+[Guid]::NewGuid().ToString('N')
$report=[ordered]@{encrypted=$false;restoreVerified=$false;originalDatabaseUntouched=$true;privateOutputWithheld=$true}
try{
 if($RestoreFrom){
  $sourceBackup=[IO.Path]::GetFullPath($RestoreFrom)
  if([IO.Path]::GetDirectoryName($sourceBackup) -ne $backupRoot -or [IO.Path]::GetExtension($sourceBackup) -ne '.dpapi' -or ((Get-Item -LiteralPath $sourceBackup).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Restore input must be a regular encrypted file directly in the backup folder.'}
  $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($sourceBackup),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
  $VerifyRestore=$true
 }else{$plain=Invoke-DatabaseBytes @('exec',$container,'pg_dump','-U','card_advisor','-d',$sourceDatabase,'--format=custom','--no-owner','--no-acl') $null}
 $encrypted=[Security.Cryptography.ProtectedData]::Protect($plain,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
 $backupName=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N')+'.pgdump.dpapi';$target=Join-Path $backupRoot $backupName
 $stream=[IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.Write($encrypted,0,$encrypted.Length)}finally{$stream.Dispose()};$report.encrypted=$true
 $recovered=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($target),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
 if(![Security.Cryptography.CryptographicOperations]::FixedTimeEquals([Security.Cryptography.SHA256]::HashData($plain),[Security.Cryptography.SHA256]::HashData($recovered))){throw 'Backup integrity check failed.'}
 if($VerifyRestore){
  if($restoreDatabase -notmatch '^card_advisor_restore_[a-f0-9]{32}$'){throw 'Unsafe restore target.'}
  $null=Database-Text 'postgres' ('CREATE DATABASE '+$restoreDatabase);$created=$true
  $restoredOutput=Invoke-DatabaseBytes @('exec','-i',$container,'pg_restore','-U','card_advisor','-d',$restoreDatabase,'--exit-on-error','--no-owner','--no-acl') $recovered;[Array]::Clear($restoredOutput,0,$restoredOutput.Length)
  # Apply the current deletion registry, even when the chosen backup is older.
  $null=Database-Text $restoreDatabase 'CREATE TABLE IF NOT EXISTS deleted_user_tombstones(user_id uuid PRIMARY KEY,deleted_at timestamptz NOT NULL DEFAULT now())'
  $deleted=Database-Text $sourceDatabase 'SELECT user_id FROM deleted_user_tombstones'
  foreach($line in ($deleted -split "`n")){if(!$line.Trim()){continue};$id=[Guid]::Empty;if(![Guid]::TryParse($line.Trim(),[ref]$id)){throw 'Invalid deletion registry.'};$null=Database-Text $restoreDatabase ("DELETE FROM app_users WHERE id='"+$id.ToString()+"'; INSERT INTO deleted_user_tombstones(user_id) VALUES ('"+$id.ToString()+"') ON CONFLICT DO NOTHING;")}
  if((Database-Text $restoreDatabase 'SELECT count(*) FROM app_users u JOIN deleted_user_tombstones d ON u.id=d.user_id') -ne '0'){throw 'Deleted account survived restore filtering.'}
  $report.deletedUsersFiltered=$true;$report.deletionEntriesApplied=@($deleted -split "`n"|Where-Object{$_.Trim()}).Count
  if(!$RestoreFrom){foreach($table in @('app_users' ,'cards','ledger_entries','cancellations','financial_transactions','financial_card_mappings','financial_performance_reviews','financial_benefit_reviews')){
   $source=Database-Text $sourceDatabase ('SELECT count(*) FROM '+$table);$copy=Database-Text $restoreDatabase ('SELECT count(*) FROM '+$table);if($source -ne $copy){throw 'Restore counts differ; values withheld.'}
  }};$report.restoreVerified=$true;$report.restoreMode=if($RestoreFrom){'OLDER_BACKUP_VERIFIED_ONLY'}else{'FRESH_BACKUP_COUNTS_VERIFIED'}
 }
 $report.backupFile=$backupName
}finally{
 if($created){if($restoreDatabase -notmatch '^card_advisor_restore_[a-f0-9]{32}$'){throw 'Unsafe cleanup target.'};$null=Database-Text 'postgres' ('DROP DATABASE '+$restoreDatabase)}
 if($plain){[Array]::Clear($plain,0,$plain.Length)};if($recovered){[Array]::Clear($recovered,0,$recovered.Length)}
 $report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot '.run\backup-restore.json') -Encoding utf8
}
$report | ConvertTo-Json
