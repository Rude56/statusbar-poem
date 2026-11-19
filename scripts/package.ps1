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

$apks = @()
$src = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $src)) { throw "APK not found: $src" }
$dst = Join-Path $dist "statusbar-poem-$version.apk"
Copy-Item $src $dst -Force
$apks += $dst

$srcZip = Join-Path $dist "statusbar-poem-$version-src.zip"
if (Test-Path $srcZip) { Remove-Item $srcZip -Force }

$staging = Join-Path $env:TEMP "poem-src-$([guid]::NewGuid().ToString('N').Substring(0,8))"
New-Item -ItemType Directory -Force -Path $staging | Out-Null
$exclude = @('.git', '.gradle', '.kotlin', '.toolchain', 'build', 'dist', '.idea',
             'local.properties', 'keystore.properties', 'keystore')
$excludeFiles = @('*.keystore', '*.jks', '*.b64')

function Copy-Tree([string]$from, [string]$to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    Get-ChildItem $from -Force | Where-Object {
        $skip = $exclude -contains $_.Name -or $_.Name -like '*.iml'
        foreach ($p in $excludeFiles) { if ($_.Name -like $p) { $skip = $true } }
        -not $skip
    } | ForEach-Object {
        if ($_.PSIsContainer) {
            Copy-Tree $_.FullName (Join-Path $to $_.Name)
        } else {
            Copy-Item $_.FullName (Join-Path $to $_.Name)
        }
    }
}
Copy-Tree $root (Join-Path $staging 'statusbar-poem')

Compress-Archive -Path (Join-Path $staging 'statusbar-poem') -DestinationPath $srcZip -Force
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue

$lines = @()
foreach ($f in ($apks + $srcZip)) {
    $hash = (Get-FileHash $f -Algorithm SHA256).Hash.ToLower()
    $name = Split-Path $f -Leaf
    $size = (Get-Item $f).Length
    $lines += "$hash  $name  ($size bytes)"
    Write-Output "$hash  $name"
}
$shaFile = Join-Path $dist 'SHA256SUMS.txt'
Set-Content -Path $shaFile -Value $lines -Encoding ascii

Write-Output ''
Write-Output "dist:"
Get-ChildItem $dist | ForEach-Object {
    Write-Output ("  {0}  ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB))
}
