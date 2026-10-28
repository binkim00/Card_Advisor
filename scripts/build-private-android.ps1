param([Parameter(Mandatory=$true)][string]$ApiUrl)
$ErrorActionPreference='Stop'
if($ApiUrl -notmatch '^https://[A-Za-z0-9.-]+(?::[0-9]+)?$'){throw 'Use an HTTPS host/port only'}
$root=Split-Path $PSScriptRoot -Parent
if(!(Test-Path (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'))){throw 'Set ANDROID_HOME to the Android SDK'}
$gradle=Get-ChildItem (Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-8.14.3-bin') -Filter gradle.bat -Recurse | Select-Object -First 1
if(!$gradle){throw 'Install the project Gradle 8.14.3 runtime first'}
Push-Location (Join-Path $root 'android')
try {
 & $gradle.FullName --no-daemon "-Padvisor.apiUrl=$ApiUrl" '-Padvisor.previewSuffix=.designreview' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
 if($LASTEXITCODE -ne 0){throw 'Private Android build failed'}
 $out=Join-Path $root 'outputs/private-android'
 New-Item -ItemType Directory -Force -Path $out | Out-Null
 Copy-Item app/build/outputs/apk/debug/app-debug.apk (Join-Path $out 'card-advisor-private.apk')
 Copy-Item app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk (Join-Path $out 'card-advisor-private-test.apk')
 @{apiUrl=$ApiUrl;package='dev.cardadvisor.android.designreview';sha256=(Get-FileHash (Join-Path $out 'card-advisor-private.apk') -Algorithm SHA256).Hash.ToLowerInvariant();physicalDeviceVerified=$false} | ConvertTo-Json | Set-Content (Join-Path $out 'build.json') -Encoding utf8
} finally {Pop-Location}
