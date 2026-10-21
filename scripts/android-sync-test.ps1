param([string]$Device='emulator-5554',[switch]$SkipBuildAndInstall)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
Get-Content -LiteralPath (Join-Path $root '.env') | ForEach-Object {if($_ -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}
$gradle='C:\Users\SSAFY\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3\bin\gradle.bat'
$maven='C:\Users\SSAFY\.m2\wrapper\dists\apache-maven-3.9.5\2021cb71\bin\mvn.cmd'
if(!$SkipBuildAndInstall){
 Push-Location (Join-Path $root 'android')
 try{& $gradle --no-daemon assembleDebug assembleDebugAndroidTest; if($LASTEXITCODE -ne 0){throw 'Android build failed'}}finally{Pop-Location}
 foreach($apk in @('debug/app-debug.apk','androidTest/debug/app-debug-androidTest.apk')){
  & "$env:ANDROID_HOME\platform-tools\adb.exe" -s $Device install -r (Join-Path $root "android/app/build/outputs/apk/$apk")
  if($LASTEXITCODE -ne 0){throw 'APK installation failed'}
 }
}
$previousFlag=$env:RUN_ANDROID_SYNC_TEST;$previousDevice=$env:ANDROID_TEST_DEVICE
$env:RUN_ANDROID_SYNC_TEST='true';$env:ANDROID_TEST_DEVICE=$Device
Push-Location (Join-Path $root 'backend')
try{& $maven -B '-Dtest=AndroidSyncPostgresTest' test;if($LASTEXITCODE -ne 0){throw 'Sync app/API/DB verification failed'}}finally{$env:RUN_ANDROID_SYNC_TEST=$previousFlag;$env:ANDROID_TEST_DEVICE=$previousDevice;Pop-Location}
