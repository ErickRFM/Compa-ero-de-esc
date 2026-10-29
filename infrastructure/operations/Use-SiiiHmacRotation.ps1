[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Status', 'Diagnose', 'CopyRenderSettings', 'CopySiiTarget')]
    [string]$Action,

    [string]$StoreDirectory,

    [switch]$CheckOnly,

    [switch]$ActiveSiiDestination
)

# Operator helper: never prints, accepts, generates, or writes plaintext secrets.
# Copy actions are interactive and keep the clipboard only until the operator
# confirms pasting. Run them yourself; do not send the clipboard to this chat.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$secretBytes = $null
$encodedSecret = $null
$payload = $null
$clipboardWasSet = $false
$stage = 'host.initialize'
$scriptExitCode = 0

function Write-SafeStage([string]$Name) {
    [pscustomobject]@{ Stage = $Name; Status = 'OK' } | ConvertTo-Json -Compress
}

try {
    Add-Type -AssemblyName System.Security
    Write-SafeStage $stage

    $stage = 'store.locator'
    $state = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'SiiiHmacRotationState.json') -Raw | ConvertFrom-Json
    if ($state.keyId -ne 'siii-hmac-20261025-138dedb2ba5840759868ff77844616a7') {
        throw 'Unexpected custody locator.'
    }
    if ([string]::IsNullOrWhiteSpace($StoreDirectory)) { $StoreDirectory = $state.storeDirectory }
    $store = [IO.Path]::GetFullPath($StoreDirectory)
    Write-SafeStage $stage

    $stage = 'store.exists'
    if (-not (Test-Path -LiteralPath $store -PathType Container)) { throw 'Custody missing.' }
    Write-SafeStage $stage

    $stage = 'metadata.exists'
    if (-not (Test-Path -LiteralPath (Join-Path $store 'metadata.json') -PathType Leaf)) {
        throw 'Metadata missing.'
    }
    Write-SafeStage $stage

    $stage = 'metadata.read'
    $metadata = Get-Content -LiteralPath (Join-Path $store 'metadata.json') -Raw | ConvertFrom-Json
    foreach ($field in @('keyId', 'tenantId', 'receiverOrigin', 'protection', 'userSid', 'expiresAtEpochSeconds')) {
        if ($null -eq $metadata.PSObject.Properties[$field]) { throw 'Metadata incomplete.' }
    }
    Write-SafeStage $stage

    $stage = 'user.match'
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ($metadata.userSid -ne $currentSid -or $metadata.protection -ne 'DPAPI-CurrentUser') {
        throw 'Custody does not belong to this Windows user.'
    }
    Write-SafeStage $stage

    $stage = 'metadata.identity'
    if ($metadata.keyId -ne $state.keyId -or
        $metadata.tenantId -ne '0690309f-8897-4c45-bf7f-d46c9c3a8ae1' -or
        $metadata.receiverOrigin -ne 'https://compa-ero-de-esc.onrender.com') {
        throw 'Unexpected rotation identity.'
    }
    Write-SafeStage $stage

    $stage = 'custody.acl'
    $acl = Get-Acl -LiteralPath $store
    if (-not $acl.AreAccessRulesProtected) { throw 'Custody permissions are not restricted.' }
    $rules = $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])
    foreach ($rule in $rules) {
        if ($rule.IdentityReference.Value -ne $currentSid -or
            $rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) {
            throw 'Unexpected custody permissions.'
        }
    }
    Write-SafeStage $stage

    $stage = 'secret.exists'
    $protectedFile = Join-Path $store 'secret.dpapi'
    if (-not (Test-Path -LiteralPath $protectedFile -PathType Leaf) -or
        (Get-Item -LiteralPath $protectedFile).Length -eq 0) {
        throw 'Protected secret missing.'
    }
    Write-SafeStage $stage

    if ($Action -eq 'Status') {
        [pscustomobject]@{
            StoreDirectory = $store
            KeyId = $metadata.keyId
            TenantId = $metadata.tenantId
            Protection = $metadata.protection
            ProtectedFileExists = $true
            ExpiresAtEpochSeconds = $metadata.expiresAtEpochSeconds
        } | ConvertTo-Json -Compress
        return
    }
    $stage = 'expiry.validate'
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    if ([long]$metadata.expiresAtEpochSeconds -le ($now + 3600) -or
        [long]$metadata.expiresAtEpochSeconds -gt ($now + 90L * 86400)) {
        throw 'Credential expiration is outside the bootstrap window.'
    }
    Write-SafeStage $stage

    $stage = 'secret.read'
    $protectedBytes = [IO.File]::ReadAllBytes($protectedFile)
    Write-SafeStage $stage

    $stage = 'dpapi.decrypt'
    $entropy = [Text.Encoding]::UTF8.GetBytes('companero-hmac-rotation-v1')
    $secretBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $protectedBytes,
        $entropy,
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    Write-SafeStage $stage

    $stage = 'secret.format'
    if ($secretBytes.Length -ne 32) { throw 'Unexpected secret size.' }
    $encodedSecret = [Convert]::ToBase64String($secretBytes)
    Write-SafeStage $stage

    if ($Action -eq 'CopyRenderSettings' -or $Action -eq 'Diagnose') {
        $stage = 'payload.render'
        $payload = @(
            'SIII_INBOUND_BOOTSTRAP_ENABLED=false'
            ('SIII_INBOUND_KEY_ID=' + $metadata.keyId)
            ('SIII_INBOUND_TENANT_ID=' + $metadata.tenantId)
            ('SIII_INBOUND_SECRET_BASE64=' + $encodedSecret)
            ('SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS=' + $metadata.expiresAtEpochSeconds)
        ) -join "`n"
        if (($payload -split "`n").Count -ne 5) { throw 'Unexpected Render payload.' }
        $destination = 'Render > Environment > Add from .env. Merge these five variables; save only; keep bootstrap false.'
        Write-SafeStage $stage
    }
    if ($Action -eq 'CopySiiTarget' -or $Action -eq 'Diagnose') {
        $stage = 'payload.sii'
        $target = @{}
        $target[$metadata.tenantId] = @{
            origin = $metadata.receiverOrigin
            keyId = $metadata.keyId
            secretBase64 = $encodedSecret
        }
        $payload = ConvertTo-Json -InputObject $target -Depth 4 -Compress
        $siiVariable = if ($ActiveSiiDestination) { 'SIII_COMPANION_DELIVERY_TARGETS_JSON' } else { 'SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON' }
        $destination = 'Vercel project siii > Production > ' + $siiVariable + ' (Secret/sensitive).'
        Write-SafeStage $stage
    }
    if ($CheckOnly -or $Action -eq 'Diagnose') {
        [pscustomobject]@{ Stage = 'CHECK_ONLY_OK'; Status = 'OK'; Action = $Action; KeyId = $metadata.keyId } |
            ConvertTo-Json -Compress
        return
    }

    # WinRT clipboard options prevent Windows clipboard history/cloud capture.
    # This established path is not exercised by Diagnose or CheckOnly.
    $stage = 'clipboard.initialize'
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.ApplicationModel.DataTransfer.Clipboard, Windows.ApplicationModel.DataTransfer, ContentType = WindowsRuntime]
    $null = [Windows.ApplicationModel.DataTransfer.DataPackage, Windows.ApplicationModel.DataTransfer, ContentType = WindowsRuntime]
    $null = [Windows.ApplicationModel.DataTransfer.ClipboardContentOptions, Windows.ApplicationModel.DataTransfer, ContentType = WindowsRuntime]
    $package = New-Object Windows.ApplicationModel.DataTransfer.DataPackage
    $package.SetText($payload)
    $options = New-Object Windows.ApplicationModel.DataTransfer.ClipboardContentOptions
    $options.IsAllowedInHistory = $false
    $options.IsRoamable = $false
    Write-SafeStage $stage
    $stage = 'clipboard.copy'
    if (-not [Windows.ApplicationModel.DataTransfer.Clipboard]::SetContentWithOptions($package, $options)) {
        throw 'Private clipboard operation failed.'
    }
    $clipboardWasSet = $true
    Write-SafeStage $stage
    Write-Host $destination
    Write-Host 'Payload copied privately. Paste without screenshots or chat messages.'
    $null = Read-Host 'After pasting and saving, press Enter to clear the clipboard'
} catch {
    # Do not forward exception messages, payloads, protected bytes, or values.
    [pscustomobject]@{
        Stage = $stage
        Status = 'FAILED'
        ErrorCode = 'PROTECTED_ROTATION_OPERATION_FAILED'
        ErrorType = $_.Exception.GetType().Name
    } | ConvertTo-Json -Compress
    $scriptExitCode = 1
} finally {
    if ($clipboardWasSet) {
        try {
            [Windows.ApplicationModel.DataTransfer.Clipboard]::Clear()
            Write-SafeStage 'clipboard.clear'
        } catch {
            [pscustomobject]@{ Stage = 'clipboard.clear'; Status = 'FAILED'; ErrorCode = 'PRIVATE_CLIPBOARD_CLEAR_FAILED' } |
                ConvertTo-Json -Compress
            $scriptExitCode = 1
        }
    }
    if ($null -ne $secretBytes) { [Array]::Clear($secretBytes, 0, $secretBytes.Length) }
    $encodedSecret = $null
    $payload = $null
}
exit $scriptExitCode
