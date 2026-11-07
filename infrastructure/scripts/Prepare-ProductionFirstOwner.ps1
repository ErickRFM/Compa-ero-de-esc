[CmdletBinding()]
param(
    [uri]$ApiOrigin = 'https://compa-ero-de-esc.onrender.com/',
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_.@-]{1,100}$')][string]$DeclaredOperator,
    [Parameter(Mandatory)][ValidateLength(8,240)][string]$Reason
)

# The deployment operator must control Render production settings.
# No HTTP endpoint grants elevated roles; verified account + MFA + one-time Mongo claim are required.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$password = $null; $otp = $null; $login = $null; $proof = $null; $payload = $null; $copied = $false
$email = 'erik55509@hotmail.com'
function Read-PrivateValue([string]$Label) {
    $secure = Read-Host $Label -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}
try {
    if ($ApiOrigin.Scheme -ne 'https' -or $ApiOrigin.Host -ne 'compa-ero-de-esc.onrender.com' -or
        $ApiOrigin.UserInfo -or $ApiOrigin.AbsolutePath -ne '/' -or $ApiOrigin.Query -or $ApiOrigin.Fragment) {
        throw 'Production origin must be the pinned Render endpoint'
    }
    if ($Reason -ne $Reason.Trim() -or $Reason -notmatch '^[A-Za-z0-9 .,;:_/-]{8,240}$') { throw 'Invalid reason' }
    $origin = $ApiOrigin.GetLeftPart([UriPartial]::Authority)
    $health = Invoke-RestMethod "$origin/health" -TimeoutSec 20
    if ($health.environment -ne 'production') { throw 'Not a production runtime' }
    Write-Host 'Prerequisite: verify Render has a distinct production MongoDB database, green release and no existing global owner.'
    $password = Read-PrivateValue 'Contraseña de la cuenta Hotmail verificada'
    $login = (Invoke-RestMethod "$origin/auth/login" -Method Post -ContentType 'application/json' -Body (@{
        username = $email; password = $password
    } | ConvertTo-Json) -TimeoutSec 20).data
    if (-not $login.user.emailVerified -or $login.user.email.ToLowerInvariant() -ne $email) {
        throw 'Email verification of designated owner is required'
    }
    $headers = @{ Authorization = 'Bearer ' + $login.accessToken }
    $status = (Invoke-RestMethod "$origin/auth/mfa/status" -Headers $headers -TimeoutSec 20).data
    if (-not $status.enabled -or -not $status.available) { throw 'Authenticator enrollment required' }
    $operation = [Guid]::NewGuid().ToString()
    $expiry = [DateTimeOffset]::UtcNow.AddMinutes(10).ToUnixTimeSeconds()
    $revision = [long]$login.user.authRevision
    $canonical = @('companero.prod-first-owner.v1', $login.user.id, $email, [string]$revision,
        $operation, $DeclaredOperator, $Reason, [string]$expiry, 'SUPER_ADMIN') -join "`n"
    $digest = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($canonical))
    ).ToLowerInvariant()
    $otp = Read-PrivateValue 'Código actual de tu autenticador MFA'
    $proof = (Invoke-RestMethod "$origin/auth/mfa/step-up" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
        password = $password; otp = $otp; action = 'PRIVILEGED_INVITE_ACCEPT'
        resourceId = 'global-owner'; operationId = $operation; payloadDigest = $digest
    } | ConvertTo-Json) -TimeoutSec 20).data
    $payload = @(
        'PROD_OWNER_BOOTSTRAP_ENABLED=true',
        "PROD_OWNER_BOOTSTRAP_ACCOUNT_ID=$($login.user.id)",
        "PROD_OWNER_BOOTSTRAP_EMAIL=$email",
        "PROD_OWNER_BOOTSTRAP_EXPECTED_REVISION=$revision",
        "PROD_OWNER_BOOTSTRAP_OPERATION_ID=$operation",
        "PROD_OWNER_BOOTSTRAP_OPERATOR=$DeclaredOperator",
        "PROD_OWNER_BOOTSTRAP_REASON=$Reason",
        "PROD_OWNER_BOOTSTRAP_EXPIRES_AT=$expiry",
        "PROD_OWNER_BOOTSTRAP_PROOF=$($proof.proof)"
    ) -join "`n"
    Set-Clipboard -Value $payload
    $copied = $true
    Write-Host 'Pega ahora los ajustes privados en Render production > Environment > Add from .env > Save and deploy.'
    Write-Host 'La prueba MFA es de un solo uso y vence pronto: si caduca, genera una nueva, no desactives la verificación.'
    Write-Host 'Tras verificar el rol en una sesión nueva, desactiva PROD_OWNER_BOOTSTRAP_ENABLED y elimina PROD_OWNER_BOOTSTRAP_PROOF.'
    Read-Host 'Pulsa Enter una vez pegado el contenido para borrar el portapapeles' | Out-Null
} catch {
    Write-Error 'PRODUCTION_OWNER_PREPARATION_FAILED: no se concedieron permisos; confirma verificación, MFA y entorno.'
    exit 1
} finally {
    if ($copied) { Set-Clipboard -Value '' }
    $password = $null; $otp = $null; $login = $null; $proof = $null; $payload = $null
}
