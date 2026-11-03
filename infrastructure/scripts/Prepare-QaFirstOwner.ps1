[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$QaApiOrigin,
    [Parameter(Mandatory)][string]$CandidateEmail,
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_.@-]{1,100}$')][string]$DeclaredOperator,
    [Parameter(Mandatory)][ValidateLength(8,240)][string]$Reason
)

# Disabled until interrupted-reservation recovery and environment isolation are independently certified.
throw 'QA_OWNER_BOOTSTRAP_NOT_READY: no se ha concedido autoridad.'

# The candidate enters credentials locally; the operator authorizes only a QA startup.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$password = $null; $otp = $null; $login = $null; $proof = $null; $payload = $null; $copied = $false
function Read-PrivateValue([string]$Label) {
    $secure = Read-Host $Label -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}
try {
    if ($QaApiOrigin.Scheme -ne 'https' -or $QaApiOrigin.UserInfo -or $QaApiOrigin.AbsolutePath -ne '/' -or $QaApiOrigin.Query -or $QaApiOrigin.Fragment) { throw 'Invalid QA origin' }
    if ($Reason -ne $Reason.Trim() -or $Reason -notmatch '^[A-Za-z0-9 .,;:_/-]{8,240}$') { throw 'Invalid reason' }
    $email = $CandidateEmail.Trim().ToLowerInvariant()
    if ($email -notmatch '^[a-z0-9._+-]+@[a-z0-9.-]+$' -or $email.Length -gt 128) { throw 'Invalid email' }
    $origin = $QaApiOrigin.GetLeftPart([UriPartial]::Authority)
    $health = Invoke-RestMethod "$origin/health" -TimeoutSec 20
    if ($health.environment -ne 'staging') { throw 'Non-QA runtime' }
    $password = Read-PrivateValue 'Contraseña de la cuenta candidata QA'
    $login = (Invoke-RestMethod "$origin/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = $email; password = $password } | ConvertTo-Json) -TimeoutSec 20).data
    if (-not $login.user.emailVerified -or $login.user.email.ToLowerInvariant() -ne $email) { throw 'Verified candidate required' }
    $headers = @{ Authorization = 'Bearer ' + $login.accessToken }
    $status = (Invoke-RestMethod "$origin/auth/mfa/status" -Headers $headers -TimeoutSec 20).data
    if (-not $status.enabled -or -not $status.available) { throw 'MFA required' }
    $operation = [Guid]::NewGuid().ToString()
    $expiry = [DateTimeOffset]::UtcNow.AddMinutes(10).ToUnixTimeSeconds()
    $revision = [long]$login.user.authRevision
    $canonical = @('companero.qa-first-owner.v1', $login.user.id, $email, [string]$revision, $operation, $DeclaredOperator, $Reason, [string]$expiry, 'SUPER_ADMIN') -join "`n"
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    $otp = Read-PrivateValue 'Código MFA actual (no lo pegues en el chat)'
    $proof = (Invoke-RestMethod "$origin/auth/mfa/step-up" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{ password = $password; otp = $otp; action = 'PRIVILEGED_INVITE_ACCEPT'; resourceId = 'global-owner'; operationId = $operation; payloadDigest = $digest } | ConvertTo-Json) -TimeoutSec 20).data
    $payload = @('QA_OWNER_BOOTSTRAP_ENABLED=true', "QA_OWNER_BOOTSTRAP_ACCOUNT_ID=$($login.user.id)", "QA_OWNER_BOOTSTRAP_EMAIL=$email", "QA_OWNER_BOOTSTRAP_EXPECTED_REVISION=$revision", "QA_OWNER_BOOTSTRAP_OPERATION_ID=$operation", "QA_OWNER_BOOTSTRAP_OPERATOR=$DeclaredOperator", "QA_OWNER_BOOTSTRAP_REASON=$Reason", "QA_OWNER_BOOTSTRAP_EXPIRES_AT=$expiry", "QA_OWNER_BOOTSTRAP_PROOF=$($proof.proof)") -join "`n"
    Set-Clipboard -Value $payload
    $copied = $true
    Write-Host 'Servicio QA aislado: Environment > Add from .env > pegar > Save and deploy usando una imagen previamente compilada. MFA vence en cinco minutos.'
    Write-Host 'No pegues este portapapeles en el chat. Tras confirmar, desactiva QA_OWNER_BOOTSTRAP_ENABLED y elimina QA_OWNER_BOOTSTRAP_PROOF.'
    Read-Host 'Pulsa Enter después de pegar para borrar el portapapeles' | Out-Null
} catch {
    Write-Error 'QA_OWNER_PREPARATION_FAILED: comprueba entorno, correo verificado, MFA y vigencia; el script no concedió permisos.'
    exit 1
} finally {
    if ($copied) { Set-Clipboard -Value '' }
    $password = $null; $otp = $null; $login = $null; $proof = $null; $payload = $null
}
