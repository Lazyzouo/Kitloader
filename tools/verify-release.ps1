param(
    [string]$Directory = 'build/libs'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-JarEntry([string]$JarPath, [string]$EntryPath) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath))
    try {
        $entry = $archive.GetEntry($EntryPath)
        if ($null -eq $entry) { throw "Missing $EntryPath in $JarPath" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}

$expected = @('en.us.jar', 'zh.cn.jar')
foreach ($name in $expected) {
    $jar = Join-Path $Directory $name
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing release asset: $jar" }
    $config = Read-JarEntry $jar 'config.yml'
    $expectedLanguage = if ($name -eq 'en.us.jar') { 'en_US' } else { 'zh_CN' }
    if ($config -notmatch "(?m)^language:\s*$expectedLanguage\s*$") {
        throw "Unexpected language preset in $name"
    }
    $pluginYml = Read-JarEntry $jar 'plugin.yml'
    if ($pluginYml -notmatch "author:\s*Lazyz") { throw "Unexpected author metadata in $name" }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash
    Write-Host "$name SHA-256 $hash"
}

Write-Host 'Release package verification passed.'
