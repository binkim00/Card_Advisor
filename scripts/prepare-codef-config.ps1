$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$local=Join-Path $root '.local'
$target=Join-Path $local 'codef.properties'
New-Item -ItemType Directory -Path $local -Force | Out-Null
if(!(Test-Path -LiteralPath $target)){Copy-Item -LiteralPath (Join-Path $root 'config/codef.example.properties') -Destination $target}
$who=[System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $target /inheritance:r /grant:r "${who}:F" 'SYSTEM:F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Cannot protect local configuration'}
Write-Output 'Local CODEF configuration prepared; existing values preserved; no provider calls made.'
