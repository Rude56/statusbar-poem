$ErrorActionPreference = 'Stop'
# 统一以 UTF-8 无 BOM 写回源码：PowerShell 5.1 的 Set-Content -Encoding utf8 会加 BOM，
# 而 javac 遇到 BOM 会报「非法字符: '\ufeff'」，构建直接失败。
$root = Join-Path $PSScriptRoot '..\app\src'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fixed = 0
Get-ChildItem $root -Recurse -Include *.java, *.kt | ForEach-Object {
    $bytes = [IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $text = [System.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
        [IO.File]::WriteAllText($_.FullName, $text, $utf8NoBom)
        Write-Output "stripped BOM: $($_.Name)"
        $fixed++
    }
}
Write-Output "BOM stripped: $fixed file(s)"
