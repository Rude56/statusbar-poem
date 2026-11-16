# 构建 debug APK（前置：已运行 scripts/bootstrap.ps1）
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Find-Toolchain([string]$start) {
    $dir = $start
    for ($i = 0; $i -lt 4 -and $dir; $i++) {
        $cand = Join-Path $dir '.toolchain'
        if (Test-Path (Join-Path $cand 'gradle\gradle-9.6.0\bin\gradle.bat')) { return $cand }
        $parent = Split-Path $dir -Parent
        if ($parent -eq $dir) { break }
        $dir = $parent
    }
    throw "未找到 .toolchain（请先运行 scripts/bootstrap.ps1）"
}
$tc = Find-Toolchain $root

$env:JAVA_HOME = (Get-ChildItem (Join-Path $tc 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $tc 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $tc 'android-home'
$env:GRADLE_USER_HOME = Join-Path $tc 'gradle-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME | Out-Null

$gradle = Join-Path $tc 'gradle\gradle-9.6.0\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw "Gradle not found: $gradle; run scripts/bootstrap.ps1 first" }

Push-Location $root
try {
    # 源码以 UTF-8 无 BOM 保存（Windows 的 Set-Content 会加 BOM，javac 会报「非法字符」）
    & (Join-Path $PSScriptRoot 'strip-bom.ps1')
    & $gradle :app:assembleDebug --stacktrace --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradle assembleDebug failed" }
} finally {
    Pop-Location
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not produced: $apk" }
Write-Output ''
Write-Output "APK: $apk"
Write-Output ("size: {0:N1} MB" -f ((Get-Item $apk).Length / 1MB))
