param(
    [string]$ApiBaseUrl = 'https://compa-ero-de-esc.onrender.com',
    [string]$Email = 'erik55509@hotmail.com',
    [string]$Operator = 'ErickRFM',
    [string]$Reason = 'Primer propietario autorizado de Companero'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$keyDirectory = Join-Path $repoRoot '.prod-owner-operator'
$publicKeyPath = Join-Path $keyDirectory 'operator-public-key.txt'
$privateKeyPath = Join-Path $keyDirectory 'operator-private-pkcs8.dpapi'
$signerSourcePath = Join-Path $keyDirectory 'SignOwnerDigest.java'
$signerClassPath = Join-Path $keyDirectory 'SignOwnerDigest.class'
$environmentFilePath = Join-Path $keyDirectory 'bootstrap.env'

function Convert-SecureStringToPlainText {
    param([System.Security.SecureString]$Value)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)

    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function ConvertFrom-Base64Url {
    param([string]$Value)

    $base64 = $Value.Replace('-', '+').Replace('_', '/')
    switch ($base64.Length % 4) {
        2 { $base64 += '==' }
        3 { $base64 += '=' }
    }
    [Convert]::FromBase64String($base64)
}

function Set-PrivateFileAcl {
    param([string]$Path)

    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner([System.Security.Principal.NTAccount]::new($identity))
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

if (-not (Test-Path -LiteralPath $publicKeyPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $privateKeyPath -PathType Leaf)) {
    throw 'No existe una clave Ed25519 local. Ejecuta primero New-ProdOwnerOperatorKey.ps1.'
}
if (-not (Test-Path -LiteralPath $signerClassPath -PathType Leaf)) {
    $javaSource = @'
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class SignOwnerDigest {
  public static void main(String[] args) throws Exception {
    var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    var privateValue = input.readLine();
    var publicValue = input.readLine();
    var digest = input.readLine();
    var decoder = Base64.getUrlDecoder();
    var factory = KeyFactory.getInstance("Ed25519");
    var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(privateValue)));
    var publicKey = factory.generatePublic(new X509EncodedKeySpec(decoder.decode(publicValue)));
    var signer = Signature.getInstance("Ed25519");
    signer.initSign(privateKey);
    signer.update(digest.getBytes(StandardCharsets.UTF_8));
    var signature = signer.sign();
    var verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(publicKey);
    verifier.update(digest.getBytes(StandardCharsets.UTF_8));
    if (!verifier.verify(signature)) throw new IllegalStateException("Operator key pair did not match");
    System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
  }
}
'@
    [System.IO.File]::WriteAllText($signerSourcePath, $javaSource, [System.Text.UTF8Encoding]::new($false))
    $javac = (Get-Command javac.exe -ErrorAction Stop).Source
    & $javac $signerSourcePath
    if ($LASTEXITCODE -ne 0) { throw 'Local Ed25519 signer could not be compiled.' }
}

$email = $Email.Trim().ToLowerInvariant()
$passwordSecure = Read-Host 'Contraseña de la cuenta (entrada oculta)' -AsSecureString
$password = Convert-SecureStringToPlainText $passwordSecure
$otpSecure = $null
$otp = $null
$login = $null
$authorizationHeaders = $null
$proof = $null
$privateKeySecure = $null
$privateKeyPlainText = $null
$process = $null
$digest = $null
$canonical = $null
$signature = $null
$environmentWritten = $false

try {
    $loginBody = @{ username = $email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -ContentType 'application/json' -Body $loginBody -TimeoutSec 45
    $authorizationHeaders = @{ Authorization = "Bearer $($login.data.accessToken)" }
    $mfaStatus = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/auth/mfa/status" -Headers $authorizationHeaders -TimeoutSec 45
    $user = $login.data.user
    $roles = @($user.roles | ForEach-Object { [string]$_ })
    $allowedRoles = @('STUDENT', 'WORKSHOP_PARTICIPANT')
    $candidateReady =
        $user.accountStatus -eq 'ACTIVE' -and
        $user.emailVerified -eq $true -and
        $user.registrationAccountType -eq 'OWNER_INVITED' -and
        $user.authRevision -ge 0 -and
        -not $user.institutionId -and
        $roles.Count -gt 0 -and
        @($roles | Where-Object { $_ -notin $allowedRoles }).Count -eq 0 -and
        -not ($roles -contains 'SUPER_ADMIN')

    [pscustomobject]@{
        AccountId = $user.id
        AccountStatus = $user.accountStatus
        EmailVerified = $user.emailVerified
        RegistrationType = $user.registrationAccountType
        Roles = $roles -join ','
        AuthRevision = $user.authRevision
        InstitutionScoped = [bool]$user.institutionId
        MfaEnabled = $mfaStatus.data.enabled
        MfaAvailable = $mfaStatus.data.available
    } | Format-List

    if (-not $candidateReady) {
        throw 'La cuenta no cumple los requisitos del candidato propietario. No se preparó el bootstrap.'
    }
    if (-not $mfaStatus.data.enabled -or -not $mfaStatus.data.available) {
        throw 'MFA no está habilitado y disponible para esta cuenta. No se preparó el bootstrap.'
    }
    $operationId = [guid]::NewGuid().ToString('N')
    $expiresAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 600
    $revision = [long]$user.authRevision
    $canonical = @(
        'companero.prod-first-owner.v1',
        [string]$user.id,
        $email,
        [string]$revision,
        $operationId,
        $Operator,
        $Reason,
        [string]$expiresAt,
        'SUPER_ADMIN'
    ) -join "`n"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digestBytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
        $digest = -join ($digestBytes | ForEach-Object { $_.ToString('x2') })
    } finally {
        $sha.Dispose()
    }

    Write-Host "La prueba MFA siguiente autoriza conceder SUPER_ADMIN a $email (cuenta $($user.id)), operación $operationId, ámbito global-owner. Esta sesión no cambia roles; sólo prepara el comando firmado que se importará en Render después." -ForegroundColor Yellow
    $otpSecure = Read-Host 'Código MFA actual para autorizar este comando (entrada oculta)' -AsSecureString
    $otp = Convert-SecureStringToPlainText $otpSecure
    $stepUpBody = @{
        password = $password
        otp = $otp
        action = 'PRIVILEGED_INVITE_ACCEPT'
        resourceId = 'global-owner'
        operationId = $operationId
        payloadDigest = $digest
    } | ConvertTo-Json -Compress
    $stepUp = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/mfa/step-up" -Headers $authorizationHeaders -ContentType 'application/json' -Body $stepUpBody -TimeoutSec 45
    $proof = [string]$stepUp.data.proof
    if ($proof -notmatch '^[A-Za-z0-9_-]{43}$') { throw 'La API no devolvió una prueba MFA válida.' }

    $privateKeyProtected = (Get-Content -LiteralPath $privateKeyPath -Raw).Trim()
    $privateKeySecure = ConvertTo-SecureString -String $privateKeyProtected
    $privateKeyPlainText = Convert-SecureStringToPlainText $privateKeySecure
    $publicKey = (Get-Content -LiteralPath $publicKeyPath -Raw).Trim()
    if ($publicKey -notmatch '^[A-Za-z0-9_-]{40,200}$') { throw 'El trust anchor local no tiene el formato Base64URL que exige la API.' }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = (Get-Command java.exe -ErrorAction Stop).Source
    $startInfo.Arguments = "-cp `"$keyDirectory`" SignOwnerDigest"
    $startInfo.WorkingDirectory = $keyDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'No se pudo iniciar el firmador local.' }
    $process.StandardInput.WriteLine($privateKeyPlainText)
    $process.StandardInput.WriteLine($publicKey)
    $process.StandardInput.WriteLine($digest)
    $process.StandardInput.Close()
    $signature = $process.StandardOutput.ReadToEnd().Trim()
    $signerError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or $signature -notmatch '^[A-Za-z0-9_-]{80,120}$') {
        throw 'La firma Ed25519 local no pasó su autoverificación.'
    }

    $environment = @(
        'PROD_OWNER_BOOTSTRAP_ENABLED=true'
        'PROD_OWNER_ISOLATION_CERTIFIED=true'
        "PROD_OWNER_BOOTSTRAP_ACCOUNT_ID=$($user.id)"
        "PROD_OWNER_BOOTSTRAP_EMAIL=$email"
        "PROD_OWNER_BOOTSTRAP_EXPECTED_REVISION=$revision"
        "PROD_OWNER_BOOTSTRAP_OPERATION_ID=$operationId"
        "PROD_OWNER_BOOTSTRAP_OPERATOR=$Operator"
        "PROD_OWNER_BOOTSTRAP_REASON=$Reason"
        "PROD_OWNER_BOOTSTRAP_EXPIRES_AT=$expiresAt"
        "PROD_OWNER_BOOTSTRAP_PROOF=$proof"
        "PROD_OWNER_BOOTSTRAP_TRUSTED_OPERATOR_PUBLIC_KEY=$publicKey"
        "PROD_OWNER_BOOTSTRAP_AUTHORIZATION=$signature"
    )
    $requiredKeys = @(
        'PROD_OWNER_BOOTSTRAP_ENABLED',
        'PROD_OWNER_ISOLATION_CERTIFIED',
        'PROD_OWNER_BOOTSTRAP_ACCOUNT_ID',
        'PROD_OWNER_BOOTSTRAP_EMAIL',
        'PROD_OWNER_BOOTSTRAP_EXPECTED_REVISION',
        'PROD_OWNER_BOOTSTRAP_OPERATION_ID',
        'PROD_OWNER_BOOTSTRAP_OPERATOR',
        'PROD_OWNER_BOOTSTRAP_REASON',
        'PROD_OWNER_BOOTSTRAP_EXPIRES_AT',
        'PROD_OWNER_BOOTSTRAP_PROOF',
        'PROD_OWNER_BOOTSTRAP_TRUSTED_OPERATOR_PUBLIC_KEY',
        'PROD_OWNER_BOOTSTRAP_AUTHORIZATION'
    )
    $presentKeys = @($environment | ForEach-Object { ($_ -split '=', 2)[0] })
    $missingKeys = @($requiredKeys | Where-Object { $_ -notin $presentKeys })
    if ($missingKeys.Count -gt 0) { throw "El comando no cumple el contrato de OwnerBootstrap.parseProduction; faltan: $($missingKeys -join ', ')" }
    $environmentText = $environment -join "`r`n"
    [System.IO.File]::WriteAllText($environmentFilePath, $environmentText + "`r`n", [System.Text.UTF8Encoding]::new($false))
    $environmentWritten = $true
    Set-PrivateFileAcl -Path $environmentFilePath

    Write-Host 'PREPARACIÓN COMPLETA: el archivo local de configuración temporal quedó listo y la firma Ed25519 se verificó localmente.' -ForegroundColor Green
    Write-Host 'El archivo incluye el trust anchor del operador y PROD_OWNER_ISOLATION_CERTIFIED=true; impórtalo solo después de certificar el aislamiento de producción.' -ForegroundColor Yellow
    Write-Host 'No pegues ni compartas el archivo, la prueba MFA, la firma o tokens en el chat. El archivo se importará únicamente en Render producción.'
    Write-Host "Archivo listo: $environmentFilePath"
    Write-Host 'La prueba MFA vence en 5 minutos; la autorización del comando vence en 10 minutos.'
} catch {
    if ($environmentWritten -and (Test-Path -LiteralPath $environmentFilePath -PathType Leaf)) {
        Remove-Item -LiteralPath $environmentFilePath -Force
    }
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode) {
        Write-Host "PREPARACIÓN FALLÓ: la API respondió HTTP $statusCode. No compartas el cuerpo de la respuesta." -ForegroundColor Red
    } else {
        Write-Host "PREPARACIÓN FALLÓ: $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
} finally {
    $password = $null
    $otp = $null
    $loginBody = $null
    $stepUpBody = $null
    $environment = $null
    $authorizationHeaders = $null
    $proof = $null
    $signature = $null
    $canonical = $null
    $privateKeyPlainText = $null
    $privateKeyProtected = $null
    $publicKey = $null
    $stepUp = $null
    $user = $null
    $mfaStatus = $null
    if ($privateKeySecure) { $privateKeySecure.Dispose() }
    if ($passwordSecure) { $passwordSecure.Dispose() }
    if ($otpSecure) { $otpSecure.Dispose() }
    if ($login) {
        $login.data.accessToken = $null
        $login.data.refreshToken = $null
    }
    if ($process) { $process.Dispose() }
}
