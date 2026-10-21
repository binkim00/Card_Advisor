param([ValidateSet('build','wrapper','ui-test','monthly-test','classification-test','budget-test','fixed-test','lifecycle-test','cold-start-test','diagnostics-test')][string]$Task='build')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$gradle=Get-ChildItem -Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-bin" -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if(!$gradle){throw 'Gradle 8.14.3 is required.'}
Push-Location (Join-Path $projectRoot 'android')
try {
 switch($Task) {
  'diagnostics-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --no-daemon }
  'wrapper' { & $gradle wrapper --gradle-version 8.14.3 --no-daemon }
  'cold-start-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'lifecycle-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'fixed-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'budget-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'classification-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'monthly-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  'ui-test' { & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon }
  default { & $gradle :app:assembleDebug --no-daemon }
 }
 if($LASTEXITCODE -ne 0){throw "Gradle failed: $LASTEXITCODE"}
} finally { Pop-Location }
if($Task -eq 'ui-test'){& (Join-Path $PSScriptRoot 'android-ui-smoke.ps1')}

if($Task -eq 'monthly-test'){& (Join-Path $PSScriptRoot 'android-month-state-smoke.ps1')}

if($Task -eq 'classification-test'){& (Join-Path $PSScriptRoot 'android-classification-smoke.ps1')}

if($Task -eq 'budget-test'){& (Join-Path $PSScriptRoot 'android-budget-smoke.ps1')}

if($Task -eq 'fixed-test'){& (Join-Path $PSScriptRoot 'android-fixed-smoke.ps1')}

if($Task -eq 'lifecycle-test'){& (Join-Path $PSScriptRoot 'android-lifecycle-smoke.ps1')}

if($Task -eq 'cold-start-test'){
 $python=Join-Path $env:USERPROFILE 'miniforge3\python.exe'
 if(!(Test-Path -LiteralPath $python)){$python=(Get-Command python -ErrorAction Stop).Source}
 & $python (Join-Path $PSScriptRoot 'android-coldstart-smoke.py')
 if($LASTEXITCODE -ne 0){throw 'Cold-start verification failed; inspect .run/coldstart'}
}
if($Task -eq 'diagnostics-test'){
 $python=Join-Path $env:USERPROFILE 'miniforge3\python.exe'
 if(!(Test-Path -LiteralPath $python)){$python=(Get-Command python -ErrorAction Stop).Source}
 & $python (Join-Path $PSScriptRoot 'android-diagnostics-smoke.py')
 if($LASTEXITCODE -ne 0){throw 'Diagnostics verification failed; inspect .run/diagnostics'}
}
