# 本地工具链自举：JDK 21 / Android cmdline-tools / Gradle 9.6.0 / SDK 37
# 全部下载到 .toolchain/（已在 .gitignore 中忽略，不进仓库）
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tc = Join-Path $root '.toolchain'
$dl = Join-Path $tc 'dl'
New-Item -ItemType Directory -Force -Path $dl, (Join-Path $tc 'jdk'), (Join-Path $tc 'gradle'), (Join-Path $tc 'sdk') | Out-Null

$fetch = Join-Path $root 'scripts\fetch.mjs'

function Get-Artifact([string]$url, [string]$dest) {
    if (Test-Path $dest) {
        $expected = 0
        Write-Output "skip (exists): $dest"
        return
    }
    Write-Output "downloading $url"
    node $fetch $url $dest
    if ($LASTEXITCODE -ne 0) { throw "download failed: $url" }
}

# ---- JDK 21 ----
$jdkZip = Join-Path $dl 'jdk21.zip'
Get-Artifact 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkZip
if (-not (Get-ChildItem (Join-Path $tc 'jdk') -Directory -ErrorAction SilentlyContinue)) {
    & 'C:\Program Files\7-Zip\7z.exe' x $jdkZip -o(Join-Path $tc 'jdk') -y | Out-Null
}

# ---- Gradle 9.6.0 ----
$gradleZip = Join-Path $dl 'gradle-9.6.0-bin.zip'
Get-Artifact 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip' $gradleZip
if (-not (Test-Path (Join-Path $tc 'gradle\gradle-9.6.0'))) {
    & 'C:\Program Files\7-Zip\7z.exe' x $gradleZip -o(Join-Path $tc 'gradle') -y | Out-Null
}

# ---- Android SDK 组件（platform 37.0 / build-tools 37.0.0）----
$sdk = Join-Path $tc 'sdk'
$platformZip = Join-Path $dl 'platform-37.0.zip'
Get-Artifact 'https://dl.google.com/android/repository/platform-37.0_r02.zip' $platformZip
$btZip = Join-Path $dl 'build-tools-37.zip'
Get-Artifact 'https://dl.google.com/android/repository/build-tools_r37_windows.zip' $btZip

$platformDir = Join-Path $sdk 'platforms\android-37'
if (-not (Test-Path (Join-Path $platformDir 'android.jar'))) {
    $tmp = Join-Path $sdk 'tmp-platform'
    & 'C:\Program Files\7-Zip\7z.exe' x $platformZip -o$tmp -y | Out-Null
    New-Item -ItemType Directory -Force -Path $platformDir | Out-Null
    Get-ChildItem (Join-Path $tmp 'android-37.0') | ForEach-Object {
        Move-Item $_.FullName (Join-Path $platformDir $_.Name) -Force
    }
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

$btDir = Join-Path $sdk 'build-tools\37.0.0'
if (-not (Test-Path (Join-Path $btDir 'aapt2.exe'))) {
    $tmp = Join-Path $sdk 'tmp-bt'
    & 'C:\Program Files\7-Zip\7z.exe' x $btZip -o$tmp -y | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $btDir) | Out-Null
    Move-Item (Join-Path $tmp 'android-37.0') $btDir -Force
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

# ---- SDK licenses（AGP 需要）----
$licDir = Join-Path $sdk 'licenses'
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
$licenses = @{
    'android-sdk-license'         = '24333f8a63b6825ea9c5514f83c2829b004d1fee'
    'android-sdk-preview-license' = '84831b9409646a918e30573bab4c9c91346d8abd'
}
foreach ($k in $licenses.Keys) {
    Set-Content -Path (Join-Path $licDir $k) -Value $licenses[$k] -Encoding ascii
}

# ---- local.properties ----
$sdkEscaped = $sdk.Replace('\', '\\')
$localProps = Join-Path $root 'local.properties'
Set-Content -Path $localProps -Value "sdk.dir=$sdkEscaped" -Encoding ascii

Write-Output ''
Write-Output 'Bootstrap done.'
Write-Output "JAVA_HOME    = $(Get-ChildItem (Join-Path $tc 'jdk') -Directory | Select-Object -First 1).FullName"
Write-Output "ANDROID_HOME = $sdk"
Write-Output 'Now run: powershell -File scripts/build.ps1'
