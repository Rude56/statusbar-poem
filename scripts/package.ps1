$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $root 'dist'
$version = (Select-String -Path (Join-Path $root 'app\build.gradle') -Pattern 'versionName\s+"([^"]+)"').Matches[0].Groups[1].Value
if (-not $version) { throw "读不到 versionName" }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$gradlew = Join-Path $root 'gradlew.bat'
if (-not (Test-Path $gradlew)) { throw "缺少 gradlew.bat" }

Push-Location $root
try {
    & $gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }
} finally {
    Pop-Location
}

$src = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $src)) { throw "APK not found: $src" }

$dst = Join-Path $dist "statusbar-poem-$version.apk"
Copy-Item $src $dst -Force

$hash = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $dst).Length
Write-Output ''
Write-Output "APK: $dst"
Write-Output ("size: {0:N2} MB" -f ($size / 1MB))
Write-Output "sha256: $hash"
