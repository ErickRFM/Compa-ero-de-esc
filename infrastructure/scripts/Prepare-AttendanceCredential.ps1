[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Status', 'ConfigureSiii', 'CopyRenderSettings')]
    [string]$Action = 'Status',
    [string]$StoreDirectory = (Join-Path $PSScriptRoot '../../build/attendance-private'),
    [string]$TenantId = '0690309f-8897-4c45-bf7f-d46c9c3a8ae1'
)

# A dedicated read key. No plaintext file, command-line value, transcript or secret output.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$stage = 'initialize'
$secret = $null
$payload = $null
$token = $null
$clipboardSet = $false
try {
    if ($TenantId -cnotmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$') { throw 'Invalid tenant' }
    $store = [IO.Path]::GetFullPath($StoreDirectory)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $entropy = [Text.Encoding]::UTF8.GetBytes('companero-attendance-read-v1')
    $metadataFile = Join-Path $store 'metadata.json'
    $secretFile = Join-Path $store 'secret.dpapi'
    if ($Action -eq 'Prepare') {
        $stage = 'prepare'
        if (Test-Path -LiteralPath $store) { throw 'Existing custody must not be overwritten' }
        New-Item -ItemType Directory -Path $store | Out-Null
        $acl = [Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true, $false)
        $acl.SetOwner($sid)
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
        Set-Acl -LiteralPath $store -AclObject $acl
        $secret = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        $protected = [Security.Cryptography.ProtectedData]::Protect($secret, $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllBytes($secretFile, $protected)
        $metadata = [ordered]@{ keyId = 'siii-attendance-' + [Guid]::NewGuid().ToString('N'); tenantId = $TenantId; scope = 'attendance:read'; receiverOrigin = 'https://compa-ero-de-esc.onrender.com'; expiresAtEpochSeconds = [DateTimeOffset]::UtcNow.AddDays(30).ToUnixTimeSeconds(); protection = 'DPAPI-CurrentUser'; userSid = $sid.Value }
        [IO.File]::WriteAllText($metadataFile, ($metadata | ConvertTo-Json))
    }
    $stage = 'custody.validate'
    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
    if ($metadata.tenantId -cne $TenantId -or $metadata.scope -cne 'attendance:read' -or
        $metadata.receiverOrigin -cne 'https://compa-ero-de-esc.onrender.com' -or
        $metadata.protection -cne 'DPAPI-CurrentUser' -or $metadata.userSid -cne $sid.Value -or
        $metadata.keyId -cnotmatch '^siii-attendance-[0-9a-f]{32}$' -or
        [long]$metadata.expiresAtEpochSeconds -le [DateTimeOffset]::UtcNow.AddHours(1).ToUnixTimeSeconds()) { throw 'Custody mismatch or expiry' }
    $acl = Get-Acl -LiteralPath $store
    if (-not $acl.AreAccessRulesProtected) { throw 'Unrestricted custody' }
    foreach ($rule in $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.IdentityReference.Value -ne $sid.Value -or $rule.AccessControlType -ne 'Allow') { throw 'Unexpected custody ACL' }
    }
    if ($null -eq $secret) { $secret = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($secretFile), $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser) }
    if ($secret.Length -ne 32) { throw 'Invalid key length' }

    if ($Action -eq 'ConfigureSiii') {
        # Local API transport keeps the new value out of tool arguments/results and terminal output.
        $stage = 'vercel.session'
        $authPath = Join-Path $env:APPDATA 'com.vercel.cli/Data/auth.json'
        $token = (Get-Content -LiteralPath $authPath -Raw | ConvertFrom-Json).token
        if ([string]::IsNullOrWhiteSpace($token)) { throw 'Vercel session missing' }
        $headers = @{ Authorization = 'Bearer ' + $token }
        $projectId = 'prj_5OMS0EPuwS3cuQF4ICqBYBF6UNlT'
        $teamId = 'team_yJjgpau6x2SsEeKXBw09sE4a'
        $stage = 'vercel.project'
        $project = Invoke-RestMethod -Uri "https://api.vercel.com/v9/projects/${projectId}?teamId=$teamId" -Headers $headers -TimeoutSec 20
        if ($project.id -cne $projectId -or $project.name -cne 'siii' -or $project.accountId -cne $teamId) { throw 'Unexpected Vercel project' }
        $stage = 'vercel.create'
        $targets = @{}
        $targets[$TenantId] = @{ origin = $metadata.receiverOrigin; keyId = $metadata.keyId; secretBase64 = [Convert]::ToBase64String($secret) }
        $payload = @{ key = 'SIII_COMPANION_ATTENDANCE_TARGETS_JSON'; type = 'sensitive'; visibility = 'secret'; target = @('production'); value = ($targets | ConvertTo-Json -Depth 4 -Compress) } | ConvertTo-Json -Depth 5 -Compress
        # No upsert: an existing credential/configuration is never silently replaced.
        $result = Invoke-RestMethod -Uri "https://api.vercel.com/v10/projects/${projectId}/env?teamId=$teamId" -Method Post -Headers $headers -ContentType 'application/json' -Body $payload -TimeoutSec 20
        if ($null -eq $result.PSObject.Properties['failed'] -or @($result.failed).Count -ne 0 -or
            $null -eq $result.PSObject.Properties['created'] -or $null -eq $result.created -or
            $result.created.key -cne 'SIII_COMPANION_ATTENDANCE_TARGETS_JSON' -or
            $result.created.type -cne 'sensitive' -or @($result.created.target).Count -ne 1 -or
            $result.created.target[0] -cne 'production') { throw 'Vercel did not certify secret creation' }
        if ($null -ne $result.created.PSObject.Properties['visibility'] -and $result.created.visibility -cne 'secret') { throw 'Unexpected visibility' }
        $result = $null
    }
    if ($Action -eq 'CopyRenderSettings') {
        $stage = 'render.clipboard'
        $payload = @('SIII_INBOUND_BOOTSTRAP_ENABLED=true', 'SIII_INBOUND_SCOPE=attendance:read', "SIII_INBOUND_KEY_ID=$($metadata.keyId)", "SIII_INBOUND_TENANT_ID=$($metadata.tenantId)", "SIII_INBOUND_SECRET_BASE64=$([Convert]::ToBase64String($secret))", "SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS=$($metadata.expiresAtEpochSeconds)") -join "`n"
        Set-Clipboard -Value $payload
        $clipboardSet = $true
        Write-Host 'Paste only into Render esc / Compa-ero-de-esc environment editor after the scoped bootstrap is deployed. Never paste into chat. Existing variables must be preserved.'
        Read-Host 'Press Enter after pasting to clear clipboard' | Out-Null
    }
    [pscustomobject]@{ action = $Action; status = 'OK'; keyId = $metadata.keyId; tenantId = $metadata.tenantId; scope = $metadata.scope; expiresAtEpochSeconds = $metadata.expiresAtEpochSeconds; secretPrinted = $false; custody = $store } | ConvertTo-Json -Compress
} catch {
    # Never echo exception text: HTTP errors may contain request values.
    $httpStatus = $null
    if ($_.Exception -is [Microsoft.PowerShell.Commands.HttpResponseException]) { $httpStatus = [int]$_.Exception.Response.StatusCode }
    [pscustomobject]@{ stage = $stage; status = 'FAILED'; httpStatus = $httpStatus; secretPrinted = $false } | ConvertTo-Json -Compress
    exit 1
} finally {
    if ($clipboardSet) { Set-Clipboard -Value '' }
    if ($null -ne $secret) { [Array]::Clear($secret, 0, $secret.Length) }
    $payload = $null
    $token = $null
}
