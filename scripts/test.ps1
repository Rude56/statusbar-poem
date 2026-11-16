# 运行全部 JVM 单元测试
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# 工具链可能放在项目内（.toolchain）或工作区上层，自动向上查找。
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

$gradle = Join-Path $tc 'gradle\gradle-9.6.0\bin\gradle.bat'
Push-Location $root
try {
    & $gradle :app:testDebugUnitTest --stacktrace --no-daemon
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

$results = Join-Path $root 'app\build\test-results\testDebugUnitTest'
$total = 0
$fail = 0
if (Test-Path $results) {
    Get-ChildItem $results -Filter '*.xml' | ForEach-Object {
        [xml]$doc = Get-Content $_.FullName
        $total += [int]$doc.testsuite.tests
        $fail += [int]$doc.testsuite.failures + [int]$doc.testsuite.errors
    }
}
Write-Output ''
Write-Output "tests=$total failures=$fail"
if ($code -ne 0) { throw "unit tests failed" }
