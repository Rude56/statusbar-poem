$ErrorActionPreference = 'Stop'
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
