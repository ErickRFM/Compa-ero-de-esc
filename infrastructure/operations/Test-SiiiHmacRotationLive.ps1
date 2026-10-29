# Read-only final wire checks. No clipboard, academic writes, or secret output.
param([switch]$ExpectAuthorityEnabled)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$stage = 'custody.validate'
$liveSecret = $null
$exitCode = 1
$lastHttpStatus = $null
$lastContentType = $null

try {
    $state = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'SiiiHmacRotationState.json') -Raw | ConvertFrom-Json
    $metadata = Get-Content -LiteralPath (Join-Path $state.storeDirectory 'metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.keyId -ne 'siii-hmac-20261025-138dedb2ba5840759868ff77844616a7' -or
        $metadata.tenantId -ne '0690309f-8897-4c45-bf7f-d46c9c3a8ae1' -or
        $metadata.userSid -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or
        [long]$metadata.expiresAtEpochSeconds -le [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) {
        throw 'Custody mismatch.'
    }
    $stage = 'custody.decrypt'
    $liveSecret = [Security.Cryptography.ProtectedData]::Unprotect(
        [IO.File]::ReadAllBytes((Join-Path $state.storeDirectory 'secret.dpapi')),
        [Text.Encoding]::UTF8.GetBytes('companero-hmac-rotation-v1'),
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    if ($liveSecret.Length -ne 32) { throw 'Unexpected secret size.' }

    function Invoke-SignedObservation([string]$Origin, [string]$Path, [string]$Body, [bool]$ValidSignature) {
        if ($Origin -notin @('https://compa-ero-de-esc.onrender.com', 'https://siii-three.vercel.app')) {
            throw 'Unverified destination.'
        }
        $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString([Globalization.CultureInfo]::InvariantCulture)
        $nonce = [Guid]::NewGuid().ToString('N')
        $script:stage = 'wire.hash'
        $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bodyBytes)).ToLowerInvariant()
        $canonical = @('POST', $Path, $timestamp, $nonce, $hash) -join "`n"
        $script:stage = 'wire.sign'
        $hmac = [Security.Cryptography.HMACSHA256]::new($liveSecret)
        try { $signature = [Convert]::ToHexString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant() }
        finally { $hmac.Dispose() }
        if (-not $ValidSignature) { $signature = '0' * 64 }
        $headers = @{
            'X-Integration-Key-Id' = $metadata.keyId
            'X-Integration-Timestamp' = $timestamp
            'X-Integration-Nonce' = $nonce
            'X-Integration-Signature' = 'v1=' + $signature
        }
        $script:stage = 'wire.http'
        $response = Invoke-WebRequest -Uri ($Origin + $Path) -Method Post -ContentType 'application/json' `
            -Body $Body -Headers $headers -SkipHttpErrorCheck -MaximumRedirection 0 -TimeoutSec 25
        $status = [int]$response.StatusCode
        $script:lastHttpStatus = $status
        $script:lastContentType = $response.Content.GetType().Name
        $contractVerified = $false
        $errorCode = $null
        if ($status -eq 200 -and $Path -eq '/integration/v1/projection-status') {
            $script:stage = 'wire.projection.decode'
            $value = $response.Content | ConvertFrom-Json
            $contractVerified = $value.contract -eq 'siii.projection_status.v1' -and
                $value.receipts.Count -eq 0 -and
                [Math]::Abs([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - [long]$value.observedAtEpochSeconds) -le 60
        } elseif ($status -ge 400) {
            try {
                $value = $response.Content | ConvertFrom-Json
                if ($value.error.code -match '^[A-Z][A-Z0-9_]{0,63}$') { $errorCode = $value.error.code }
            } catch { $errorCode = $null }
        }
        return [pscustomobject]@{ Status = $status; ContractVerified = $contractVerified; ErrorCode = $errorCode }
    }

    $projectionPath = '/integration/v1/projection-status'
    $projectionBody = @{ contract = 'siii.projection_status_request.v1'; eventIds = @([Guid]::NewGuid().ToString()) } |
        ConvertTo-Json -Compress
    $stage = 'projection.valid'
    $projectionValid = Invoke-SignedObservation 'https://compa-ero-de-esc.onrender.com' $projectionPath $projectionBody $true
    $stage = 'projection.invalid'
    $projectionInvalid = Invoke-SignedObservation 'https://compa-ero-de-esc.onrender.com' $projectionPath $projectionBody $false

    $authorityPath = '/api/integrations/companion/v1/authority'
    $authorityBody = @{
        contract = 'siii.academic_authority_request.v1'
        institutionId = $metadata.tenantId
        kind = 'student'
        externalId = 'HMAC_ROTATION_PROBE'
    } | ConvertTo-Json -Compress
    $stage = 'authority.valid'
    $authorityValid = Invoke-SignedObservation 'https://siii-three.vercel.app' $authorityPath $authorityBody $true
    $stage = 'authority.invalid'
    $authorityInvalid = Invoke-SignedObservation 'https://siii-three.vercel.app' $authorityPath $authorityBody $false

    $expectedAuthorityStatus = if ($ExpectAuthorityEnabled) { 404 } else { 503 }
    $expectedAuthorityCode = if ($ExpectAuthorityEnabled) { 'SOURCE_IDENTITY_UNAVAILABLE' } else { 'SOURCE_AUTHORITY_DISABLED' }
    $passed = $projectionValid.Status -eq 200 -and $projectionValid.ContractVerified -and
        $projectionInvalid.Status -eq 401 -and
        $authorityValid.Status -eq $expectedAuthorityStatus -and $authorityValid.ErrorCode -eq $expectedAuthorityCode -and
        $authorityInvalid.Status -eq 401
    [pscustomobject]@{
        KeyId = $metadata.keyId
        ProjectionValidStatus = $projectionValid.Status
        ProjectionContractVerified = $projectionValid.ContractVerified
        ProjectionInvalidStatus = $projectionInvalid.Status
        AuthorityValidStatus = $authorityValid.Status
        AuthorityValidErrorCode = $authorityValid.ErrorCode
        AuthorityInvalidStatus = $authorityInvalid.Status
        AcademicWritesPerformed = $false
        SecretPrinted = $false
        Passed = $passed
    } | ConvertTo-Json -Compress
    $exitCode = if ($passed) { 0 } else { 1 }
} catch {
    [pscustomobject]@{ Stage = $stage; Status = 'FAILED'; ErrorCode = 'FINAL_WIRE_OBSERVATION_FAILED'; ErrorType = $_.Exception.GetType().Name; InnerType = $(if ($null -ne $_.Exception.InnerException) { $_.Exception.InnerException.GetType().Name } else { $null }); HResult = $_.Exception.HResult; LastHttpStatus = $lastHttpStatus; ContentType = $lastContentType } |
        ConvertTo-Json -Compress
} finally {
    if ($null -ne $liveSecret) { [Array]::Clear($liveSecret, 0, $liveSecret.Length) }
}
exit $exitCode
