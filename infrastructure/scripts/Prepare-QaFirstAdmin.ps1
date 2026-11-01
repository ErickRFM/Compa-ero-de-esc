[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$QaApiOrigin,
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_-]{1,128}$')][string]$NativeInstitutionId,
    [Parameter(Mandatory)][string]$CandidateEmail
)

# Run locally by the verified candidate after enabling MFA in the QA app.
# Creates only a scoped MFA proof; the operator-controlled QA startup performs the audited grant.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$password = $null
$otp = $null
$login = $null
$proof = $null
$payload = $null
$copied = $false
function Read-PrivateValue([string]$Label) {
    $secure = Read-Host $Label -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}
try {
    if ($QaApiOrigin.Scheme -ne 'https' -or $QaApiOrigin.UserInfo -or $QaApiOrigin.AbsolutePath -ne '/' -or $QaApiOrigin.Query -or $QaApiOrigin.Fragment) { throw 'Invalid QA origin' }
    $origin = $QaApiOrigin.GetLeftPart([UriPartial]::Authority)
    $health = Invoke-RestMethod "$origin/health" -TimeoutSec 20
    if ($health.environment -notin @('local', 'staging')) { throw 'Non-QA runtime' }
    $password = Read-PrivateValue 'Contraseña independiente de la cuenta candidata QA'
    $login = (Invoke-RestMethod "$origin/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = $CandidateEmail; password = $password } | ConvertTo-Json) -TimeoutSec 20).data
    if (-not $login.user.emailVerified) { throw 'Verified candidate required' }
    $headers = @{ Authorization = 'Bearer ' + $login.accessToken }
    $status = (Invoke-RestMethod "$origin/auth/mfa/status" -Headers $headers -TimeoutSec 20).data
    if (-not $status.enabled -or -not $status.available) { throw 'Enroll MFA in the QA app first' }
    $operation = [Guid]::NewGuid().ToString()
    $expiry = [DateTimeOffset]::UtcNow.AddMinutes(10).ToUnixTimeSeconds()
    $revision = [long]$login.user.authRevision
    $canonical = @('companero.qa-first-admin.v1', $NativeInstitutionId, $login.user.id, [string]$revision, $operation, [string]$expiry, 'ADMIN', 'APPROVE_STAFF,MANAGE_ACADEMICS,MANAGE_USERS', 'registrationEnabled=true') -join "`n"
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    $otp = Read-PrivateValue 'Código MFA actual (no lo pegues en el chat)'
    $proof = (Invoke-RestMethod "$origin/auth/mfa/step-up" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{ password = $password; otp = $otp; action = 'PRIVILEGED_INVITE_ACCEPT'; resourceId = $NativeInstitutionId; operationId = $operation; payloadDigest = $digest } | ConvertTo-Json) -TimeoutSec 20).data
    $payload = @('QA_ADMIN_BOOTSTRAP_ENABLED=true', "QA_ADMIN_BOOTSTRAP_INSTITUTION_ID=$NativeInstitutionId", "QA_ADMIN_BOOTSTRAP_ACCOUNT_ID=$($login.user.id)", "QA_ADMIN_BOOTSTRAP_EXPECTED_REVISION=$revision", "QA_ADMIN_BOOTSTRAP_OPERATION_ID=$operation", "QA_ADMIN_BOOTSTRAP_EXPIRES_AT=$expiry", "QA_ADMIN_BOOTSTRAP_PROOF=$($proof.proof)") -join "`n"
    Set-Clipboard -Value $payload
    $copied = $true
    Write-Host 'En el servicio QA aislado de Render: Environment > Add from .env > pegar > Save and deploy. Conserva las demás variables. La prueba MFA vence en cinco minutos.'
    Write-Host 'No pegues este portapapeles en el chat. Al confirmar el arranque, desactiva QA_ADMIN_BOOTSTRAP_ENABLED y elimina QA_ADMIN_BOOTSTRAP_PROOF.'
    Read-Host 'Pulsa Enter después de pegar para borrar el portapapeles' | Out-Null
} catch {
    # Never echo driver/HTTP exceptions, response bodies, credentials or proof material.
    Write-Error 'QA_BOOTSTRAP_PREPARATION_FAILED: comprueba entorno QA, correo verificado, MFA y vigencia; no se concedieron permisos desde este script.'
    exit 1
} finally {
    if ($copied) { Set-Clipboard -Value '' }
    $password = $null; $otp = $null; $login = $null; $proof = $null; $payload = $null
}
