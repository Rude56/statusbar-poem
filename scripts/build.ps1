$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $env:JAVA_HOME) { throw "请先设置 JAVA_HOME 指向 JDK 21" }
if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $root 'local.properties'))) {
    throw "请设置 ANDROID_HOME，或在 local.properties 里写 sdk.dir"
}

$gradlew = Join-Path $root 'gradlew.bat'
if (-not (Test-Path $gradlew)) { throw "缺少 gradlew.bat" }

Push-Location $root
try {
    & (Join-Path $PSScriptRoot 'strip-bom.ps1')
    & $gradlew :app:assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradle assembleDebug failed" }
} finally {
    Pop-Location
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not produced: $apk" }
Write-Output ''
Write-Output "APK: $apk"
Write-Output ("size: {0:N1} MB" -f ((Get-Item $apk).Length / 1MB))
