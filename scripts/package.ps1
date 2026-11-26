$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $root 'dist'

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

# 从产物 APK 里读真实版本号，而不是 grep build.gradle。
#
# 不要用 "versionName='([^']+)'" 一条正则去匹配整行：aapt2 输出里还有
# platformBuildVersionName='17'，贪婪匹配可能回溯到它，读出 17。
# 按空格切词后做精确前缀匹配。
$aapt2 = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Recurse -Filter 'aapt2.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $aapt2) { throw "找不到 aapt2，请设置 ANDROID_HOME" }

$tokens = ((& $aapt2.FullName dump badging $src 2>$null | Select-Object -First 1) -split ' ')
$version = ($tokens | Where-Object { $_ -like "versionName='*'" } | Select-Object -First 1) -replace "^versionName='|'$", ''
$code = ($tokens | Where-Object { $_ -like "versionCode='*'" } | Select-Object -First 1) -replace "^versionCode='|'$", ''
if (-not $version) { throw "读不到 APK 的 versionName" }

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$dst = Join-Path $dist "statusbar-poem-$version.apk"
Copy-Item $src $dst -Force

$hash = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $dst).Length
Write-Output ''
Write-Output "APK: $dst"
Write-Output "versionCode: $code"
Write-Output ("size: {0:N2} MB" -f ($size / 1MB))
Write-Output "sha256: $hash"
