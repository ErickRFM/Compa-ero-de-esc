# Focused custody/helper checks. No key generation and no clipboard operations.
$ErrorActionPreference = 'Stop'
$helper = Join-Path $PSScriptRoot 'Use-SiiiHmacRotation.ps1'
$failed = 0

function Invoke-HelperCheck([string[]]$HelperArguments) {
    $ErrorActionPreference = 'Continue'
    $captured = & powershell.exe -NoProfile -STA -File $helper @HelperArguments 2>&1 | Out-String
    return @{ ExitCode = $LASTEXITCODE; Output = $captured }
}

$missing = Invoke-HelperCheck @('-Action', 'Status', '-StoreDirectory', 'C:\proyectos\esc\infrastructure\operations\missing-custody-no-secret')
if ($missing.ExitCode -eq 0 -or $missing.Output -notmatch 'store.exists.*FAILED') {
    Write-Output 'FAIL: missing custody must report a safe store.exists stage and fail closed'
    $failed++
} else { Write-Output 'PASS: missing custody fails closed with stage diagnostic' }

foreach ($action in @('CopyRenderSettings', 'CopySiiTarget')) {
    $result = Invoke-HelperCheck @('-Action', $action, '-CheckOnly')
    if ($result.ExitCode -ne 0 -or $result.Output -notmatch 'CHECK_ONLY_OK' -or
        $result.Output -match 'clipboard\.' -or
        $result.Output -match 'SIII_INBOUND_SECRET_BASE64=|secretBase64"\s*:') {
        Write-Output ('FAIL: ' + $action + ' must validate the existing custody and payload without clipboard or secret output')
        $failed++
    } else { Write-Output ('PASS: ' + $action + ' custody and payload validated without clipboard') }
}
if ($failed -gt 0) { exit 1 }
Write-Output 'HELPER_CHECKS_OK'
