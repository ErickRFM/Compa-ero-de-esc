param(
    [string]$ConfigPath,
    [ValidateSet('Debug', 'Release')][string]$BuildType = 'Debug',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'apps/android/app/google-services.json'
}

# Read locally; never log the document, API key or Firebase identifiers.
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$clients = @($config.client | Where-Object {
    $_.client_info.android_client_info.package_name -ceq 'org.companerodeescuela'
})
if ($clients.Count -ne 1) { throw 'Exactly one Academic Firebase client for org.companerodeescuela is required.' }
$client = $clients[0]
$settings = @{
    COMPANERO_FIREBASE_APP_ID = [string]$client.client_info.mobilesdk_app_id
    COMPANERO_FIREBASE_PROJECT_ID = [string]$config.project_info.project_id
    COMPANERO_FIREBASE_SENDER_ID = [string]$config.project_info.project_number
    COMPANERO_FIREBASE_API_KEY = [string]$client.api_key[0].current_key
}
if ($settings.COMPANERO_FIREBASE_APP_ID -cnotmatch '^1:[0-9]{4,25}:android:[A-Za-z0-9]+$' -or
    $settings.COMPANERO_FIREBASE_PROJECT_ID -cnotmatch '^[a-z][a-z0-9-]{3,99}$' -or
    $settings.COMPANERO_FIREBASE_SENDER_ID -cnotmatch '^[0-9]{4,25}$' -or
    $settings.COMPANERO_FIREBASE_API_KEY -cnotmatch '^[A-Za-z0-9_-]{20,100}$' -or
    $settings.COMPANERO_FIREBASE_APP_ID.Split(':')[1] -cne $settings.COMPANERO_FIREBASE_SENDER_ID) {
    throw 'Invalid or inconsistent Academic Firebase configuration.'
}
if ($ValidateOnly) {
    Write-Output 'Academic Firebase configuration validated; no build executed.'
    exit 0
}

$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    Push-Location -LiteralPath $repoRoot
    try {
        & (Join-Path $repoRoot 'gradlew.bat') ":apps:android:app:assembleAcademic$BuildType" '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Academic build failed; check Gradle diagnostics.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
