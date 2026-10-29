$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$helper = Join-Path $PSScriptRoot '../Prepare-AttendanceCredential.ps1'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$store = Join-Path $repoRoot ('build/attendance-custody-test-' + [Guid]::NewGuid().ToString('N'))
function Run-Helper([string]$Action, [bool]$Expected) {
    $output = & pwsh -NoProfile -File $helper -Action $Action -StoreDirectory $store
    if (($LASTEXITCODE -eq 0) -ne $Expected) { throw 'Custody result mismatch' }
    if ($output -match 'secretBase64|SECRET_BASE64=|Bearer |secretCiphertext') { throw 'Sensitive output' }
    return $output | ConvertFrom-Json
}
$prepared = Run-Helper 'Prepare' $true
if ($prepared.scope -cne 'attendance:read' -or -not $prepared.secretPrinted -eq $false) { throw 'Unexpected purpose' }
$status = Run-Helper 'Status' $true
if ($status.keyId -cne $prepared.keyId) { throw 'Key identity changed' }
$null = Run-Helper 'Prepare' $false
$metadataFile = Join-Path $store 'metadata.json'
$original = [IO.File]::ReadAllText($metadataFile)
try {
    $metadata = $original | ConvertFrom-Json
    $metadata.scope = 'group:write'
    [IO.File]::WriteAllText($metadataFile, ($metadata | ConvertTo-Json))
    $null = Run-Helper 'Status' $false
} finally { [IO.File]::WriteAllText($metadataFile, $original) }
$null = Run-Helper 'Status' $true
Write-Output 'PASS: DPAPI roundtrip, stable identity, no overwrite, wrong-purpose rejection, secret-free output. Encrypted disposable fixture retained in ignored build/.'
