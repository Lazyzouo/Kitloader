param(
    [string]$Directory = 'build/libs',
    [string]$Version = ''
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

if ([string]::IsNullOrWhiteSpace($Version)) {
    $buildFile = Get-Content -Raw -LiteralPath 'build.gradle'
    $versionMatch = [regex]::Match($buildFile, "(?m)^version\s*=\s*'([^']+)'\s*$")
    if (-not $versionMatch.Success) { throw 'Unable to read version from build.gradle' }
    $Version = $versionMatch.Groups[1].Value
}

$expected = @("Kitloader-$Version-en.us.jar", "Kitloader-$Version-zh.cn.jar")
foreach ($name in $expected) {
    $jar = Join-Path $Directory $name
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing release asset: $jar" }
    $config = Read-JarEntry $jar 'config.yml'
    $expectedLanguage = if ($name.EndsWith('-en.us.jar')) { 'en_US' } else { 'zh_CN' }
    if ($config -notmatch "(?m)^language:\s*$expectedLanguage\s*$") {
        throw "Unexpected language preset in $name"
    }
    $pluginYml = Read-JarEntry $jar 'plugin.yml'
    if ($pluginYml -notmatch "author:\s*Lazyz") { throw "Unexpected author metadata in $name" }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash
    Write-Host "$name SHA-256 $hash"
    if ($pluginYml -notmatch "version:[ ]*'?$([regex]::Escape($Version))'?[ ]*") {
        throw "Unexpected plugin version in $name"
    }
}

Write-Host 'Release package verification passed.'
