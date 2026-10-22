param([Parameter(Mandatory)][ValidateRange(1,36500)][int]$RetainDays,[switch]$Apply)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$backupRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot '.local/backups'))
if(!$backupRoot.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid backup directory.'}
$cutoff=[DateTime]::UtcNow.AddDays(-$RetainDays)
$files=@(Get-ChildItem -LiteralPath $backupRoot -File -ErrorAction SilentlyContinue|Where-Object{$_.Name -match '^[0-9]{8}-[0-9]{6}-[a-f0-9]{32}\.pgdump\.dpapi$'}|Sort-Object LastWriteTimeUtc -Descending)
$candidates=@($files|Select-Object -Skip 1|Where-Object{$_.LastWriteTimeUtc -lt $cutoff})
foreach($file in $candidates){
 $path=[IO.Path]::GetFullPath($file.FullName)
 if([IO.Path]::GetDirectoryName($path) -ne $backupRoot -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Unexpected backup path; nothing else removed.'}
 if($Apply){Remove-Item -LiteralPath $path -Force}
}
@{mode=$(if($Apply){'APPLIED'}else{'PREVIEW_ONLY'});candidateCount=$candidates.Count;retainedDays=$RetainDays;newestBackupPreserved=$true;deletionRegistryPreserved=$true}|ConvertTo-Json
