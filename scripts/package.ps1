# 打包交付物到 dist/：APK（release + debug）+ 源码 zip + SHA256SUMS.txt
#
# release：R8 裁剪过，约 2 MB，日常使用装这个。
# debug  ：不裁剪，约 23 MB，只在需要完整堆栈/热修时用。
# 两者同一个签名，可互相覆盖安装。
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $root 'dist'
$version = 'v1.0.0'
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$apks = @()
foreach ($pair in @(@('release', 'app\build\outputs\apk\release\app-release.apk'),
                    @('debug', 'app\build\outputs\apk\debug\app-debug.apk'))) {
    $src = Join-Path $root $pair[1]
    if (-not (Test-Path $src)) {
        throw "APK not found: $src (run scripts\build.ps1 / assembleRelease first)"
    }
    $dst = Join-Path $dist ("statusbar-poem-$version-" + $pair[0] + ".apk")
    Copy-Item $src $dst -Force
    $apks += $dst
}

# 源码包：排除构建产物与工具链
$srcZip = Join-Path $dist "statusbar-poem-$version-src.zip"
if (Test-Path $srcZip) { Remove-Item $srcZip -Force }

$staging = Join-Path $env:TEMP "poem-src-$([guid]::NewGuid().ToString('N').Substring(0,8))"
New-Item -ItemType Directory -Force -Path $staging | Out-Null
$exclude = @('.git', '.gradle', '.toolchain', 'build', 'dist', '.idea', 'local.properties')

function Copy-Tree([string]$from, [string]$to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    Get-ChildItem $from -Force | Where-Object {
        $exclude -notcontains $_.Name -and $_.Name -notlike '*.iml'
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

# SHA-256
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
