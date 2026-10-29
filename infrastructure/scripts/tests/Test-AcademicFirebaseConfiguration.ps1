$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$fixturePath = Join-Path $repoRoot 'build/firebase-validation-fixture.json'
$validator = Join-Path $repoRoot 'infrastructure/scripts/Build-AcademicFirebase.ps1'
$hostExecutable = (Get-Process -Id $PID).Path
New-Item -ItemType Directory -Path (Split-Path $fixturePath -Parent) -Force | Out-Null
$fixture = @{
    project_info = @{ project_id = 'synthetic-project'; project_number = '1234567890' }
    client = @(@{
        client_info = @{
            mobilesdk_app_id = '1:1234567890:android:abcdef'
            android_client_info = @{ package_name = 'org.companerodeescuela' }
        }
        api_key = @(@{ current_key = 'synthetic_key_not_a_real_firebase_key' })
    })
}
function Assert-Validation([bool]$Expected) {
    $fixture | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $fixturePath
    $output = & $hostExecutable -NoProfile -File $validator -ConfigPath $fixturePath -ValidateOnly 2>&1
    if (($LASTEXITCODE -eq 0) -ne $Expected) { throw 'Firebase configuration validation result mismatch.' }
    if (($output | Out-String).Contains($fixture.client[0].api_key[0].current_key)) { throw 'Configuration value appeared in output.' }
}
try {
    Assert-Validation $true
    $fixture.client[0].client_info.android_client_info.package_name = 'org.companerodeescuela.gestion'
    Assert-Validation $false
    $fixture.client[0].client_info.android_client_info.package_name = 'org.companerodeescuela'
    $fixture.project_info.project_number = '9999999999'
    Assert-Validation $false
    Write-Output 'Firebase config: valid Academic accepted, Management and sender mismatch rejected, no config values logged.'
} finally {
    Remove-Item -LiteralPath $fixturePath -ErrorAction SilentlyContinue
}
exit 0
