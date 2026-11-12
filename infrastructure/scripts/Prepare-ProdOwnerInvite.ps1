[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$')]
    [string]$Recipient,
    [string]$OutputPath
)

# Creates the temporary Render environment file for the existing first-owner
# invitation flow. It does not send email, change Render, create an account,
# accept the invitation, or grant SUPER_ADMIN.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function New-OwnerInviteSecret {
    $secretBytes = [byte[]]::new(32)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($secretBytes)
        return [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    } finally {
        $random.Dispose()
        [Array]::Clear($secretBytes, 0, $secretBytes.Length)
    }
}

$canonicalRecipient = $Recipient.Trim().ToLowerInvariant()
$code = New-OwnerInviteSecret
$dispatchToken = New-OwnerInviteSecret
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $codeBytes = [Text.Encoding]::UTF8.GetBytes($code)
    try {
        $digestBytes = $hasher.ComputeHash($codeBytes)
        $codeDigest = [BitConverter]::ToString($digestBytes).Replace('-', '').ToLowerInvariant()
        [Array]::Clear($digestBytes, 0, $digestBytes.Length)
    } finally {
        [Array]::Clear($codeBytes, 0, $codeBytes.Length)
    }
} finally {
    $hasher.Dispose()
}

if ($code.Length -ne 43 -or $dispatchToken.Length -ne 43 -or $codeDigest.Length -ne 64) {
    throw 'Owner invitation generation failed verification'
}

$expiresAt = [DateTimeOffset]::UtcNow.AddHours(24).ToUnixTimeSeconds()
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    $OutputPath = Join-Path $repositoryRoot '.env.owner-invite.local'
}
$fullOutputPath = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $fullOutputPath) {
    throw 'The output file already exists; choose a new path to avoid replacing existing data.'
}

$environment = @(
    "OWNER_INVITE_EMAIL=$canonicalRecipient"
    "OWNER_INVITE_CODE=$code"
    "OWNER_INVITE_CODE_SHA256=$codeDigest"
    "OWNER_INVITE_EXPIRES_AT=$expiresAt"
    "OWNER_INVITE_DISPATCH_TOKEN=$dispatchToken"
    'PROD_OWNER_ISOLATION_CERTIFIED=true'
)
[IO.File]::WriteAllText($fullOutputPath, ($environment -join "`r`n") + "`r`n", [Text.UTF8Encoding]::new($false))

Write-Host 'Se creó el archivo temporal de variables para Render; no se imprimieron los secretos.'
Write-Host "Archivo: $fullOutputPath"
Write-Host 'Render: servicio Compa-ero-de-esc → Environment → Edit → Add from .env.'
Write-Host 'Importa solo si confirmaste APP_ENV=production, MONGODB_DATABASE=companero_dev y aislamiento de Mongo de producción.'
Write-Host 'Conserva el archivo local hasta que el correo se haya enviado; luego bórralo.'
Write-Host 'Tras HTTP 202, elimina de Render OWNER_INVITE_CODE y OWNER_INVITE_DISPATCH_TOKEN; conserva EMAIL, CODE_SHA256 y EXPIRES_AT hasta aceptar la invitación.'
Write-Host 'La API key y el remitente existentes de Resend no se modifican.'

$code = $null
$dispatchToken = $null
$codeDigest = $null
[Array]::Clear($environment, 0, $environment.Length)
