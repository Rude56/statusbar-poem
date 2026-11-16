# 运行全部 JVM 单元测试
#
# 前置条件同 build.ps1。
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
    & $gradlew :app:testDebugUnitTest --no-daemon
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
