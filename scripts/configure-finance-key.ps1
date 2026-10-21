$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$privateDirectory=Join-Path $projectRoot '.local'
$keyFile=Join-Path $privateDirectory 'finance-storage.properties'
if(Test-Path -LiteralPath $keyFile){Write-Output 'Existing finance storage key preserved.';exit 0}
New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
# Create the empty file exclusively and restrict access before writing key material.
$stream=[System.IO.File]::Open($keyFile,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::None)
$stream.Dispose()
$acl=New-Object System.Security.AccessControl.FileSecurity
$acl.SetAccessRuleProtection($true,$false)
$identity=[System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($identity,'FullControl','Allow')))
$systemSid=New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')
$acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($systemSid,'FullControl','Allow')))
Set-Acl -LiteralPath $keyFile -AclObject $acl
$keyBytes=New-Object byte[] 32
$rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
try{
 $rng.GetBytes($keyBytes)
 $content="activeKeyId=k1`nkeys.k1=$([Convert]::ToBase64String($keyBytes))`n"
 [System.IO.File]::WriteAllText($keyFile,$content,(New-Object System.Text.UTF8Encoding($false)))
}finally{[Array]::Clear($keyBytes,0,$keyBytes.Length);$rng.Dispose();$content=$null}
Write-Output 'Finance storage key created in .local/finance-storage.properties. Key material is not printed.'
