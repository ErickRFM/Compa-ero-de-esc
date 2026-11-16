$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$root = Join-Path $repoRoot '.prod-owner-operator'
$source = Join-Path $root 'GenerateOwnerKey.java'
$publicPath = Join-Path $root 'operator-public-key.txt'
$privatePath = Join-Path $root 'operator-private-pkcs8.dpapi'
if ((Test-Path -LiteralPath $publicPath -PathType Leaf) -and (Test-Path -LiteralPath $privatePath -PathType Leaf)) {
    $existingPublicKey = (Get-Content -LiteralPath $publicPath -Raw).Trim()
    Write-Output "Existing public trust anchor: $existingPublicKey"
    Write-Output 'The existing private key remains DPAPI-encrypted; it was not rotated.'
    exit 0
}
if ((Test-Path -LiteralPath $publicPath) -or (Test-Path -LiteralPath $privatePath)) {
    throw 'A partial operator key exists. Do not overwrite it until the trust anchor is reconciled.'
}
New-Item -ItemType Directory -Path $root -Force | Out-Null

$javaSource = @'
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class GenerateOwnerKey {
  public static void main(String[] args) throws Exception {
    var generator = KeyPairGenerator.getInstance("Ed25519");
    var pair = generator.generateKeyPair();
    var encoder = Base64.getUrlEncoder().withoutPadding();
    System.out.println(encoder.encodeToString(pair.getPublic().getEncoded()));
    System.out.println(encoder.encodeToString(pair.getPrivate().getEncoded()));
  }
}
'@
[System.IO.File]::WriteAllText($source, $javaSource, [System.Text.UTF8Encoding]::new($false))

$javac = (Get-Command javac.exe -ErrorAction Stop).Source
$java = (Get-Command java.exe -ErrorAction Stop).Source
& $javac $source
if ($LASTEXITCODE -ne 0) { throw 'Ed25519 key generation helper did not compile.' }
$lines = @(& $java -cp $root GenerateOwnerKey)
if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 2) { throw 'Ed25519 key generation failed.' }
$publicKey = [string]$lines[0]
$privateKey = [string]$lines[1]
if ($publicKey.Length -lt 40 -or $privateKey.Length -lt 50) { throw 'Generated key material is invalid.' }

$securePrivate = ConvertTo-SecureString -String $privateKey -AsPlainText -Force
try {
    $protectedPrivate = ConvertFrom-SecureString -SecureString $securePrivate
    Set-Content -LiteralPath $privatePath -Value $protectedPrivate -Encoding ascii
    Set-Content -LiteralPath $publicPath -Value $publicKey -Encoding ascii
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner([System.Security.Principal.NTAccount]::new($identity))
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $privatePath -AclObject $acl
} finally {
    $privateKey = $null
    $securePrivate.Dispose()
    $protectedPrivate = $null
}

Write-Output "Public trust anchor: $publicKey"
Write-Output 'Private signing key stored encrypted with Windows DPAPI for this user.'
