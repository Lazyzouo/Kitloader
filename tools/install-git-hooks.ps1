Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (git rev-parse --show-toplevel).Trim()
if ([string]::IsNullOrWhiteSpace($repositoryRoot)) {
    throw 'Run this script inside the Kitloader Git repository.'
}

git config core.hooksPath "$repositoryRoot/.githooks"
Write-Host 'Kitloader post-commit auto-push hook configured.'
