[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$')][string]$Recipient
)

# Generates a registration invitation for a separately authorized first-owner ceremony.
# Does not send mail, enable production, create an account, or grant SUPER_ADMIN.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$bytes = [byte[]]::new(32)
try {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $code = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $sha = $hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($code)) } finally { $hasher.Dispose() }
    $digest = [BitConverter]::ToString($sha).Replace('-', '').ToLowerInvariant()
    if ($code.Length -ne 43 -or $digest.Length -ne 64) {
        throw 'Owner invitation generation failed verification'
    }
    Set-Clipboard -Value $code
    Write-Host 'Código aleatorio copiado al portapapeles. No pegar en GitHub, logs ni chat.'
    Write-Host 'Configurar en Render SOLO después de certificar el aislamiento y desplegar código aprobado:'
    Write-Host "OWNER_INVITE_EMAIL=$($Recipient.Trim().ToLowerInvariant())"
    Write-Host "OWNER_INVITE_CODE_SHA256=$digest"
    $expiresAt = [DateTimeOffset]::UtcNow.AddHours(24).ToUnixTimeSeconds()
    Write-Host "OWNER_INVITE_EXPIRES_AT=$expiresAt"
    Write-Host 'PROD_OWNER_ISOLATION_CERTIFIED=true (SOLO tras certificar la migración)'
    Write-Host 'Enviar el código del portapapeles al destinatario verificado cuando producción acepte OWNER_INVITED.'
    Write-Host 'Tras registro, verificación y MFA, ejecutar el bootstrap de propietario autorizado y eliminar el hash del entorno.'
    Read-Host 'Pulsa Enter después de registrar y enviar el código para borrar el portapapeles' | Out-Null
} finally {
    Set-Clipboard -Value ''
    [Array]::Clear($bytes, 0, $bytes.Length)
}
