$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$helper = Join-Path $PSScriptRoot '../Prepare-AttendanceCredential.ps1'
$fixture = Join-Path $repoRoot ('build/attendance-transfer-test-' + [Guid]::NewGuid().ToString('N'))
$store = Join-Path $fixture 'custody'
$null = New-Item -ItemType Directory -Path (Join-Path $fixture 'com.vercel.cli/Data') -Force
[IO.File]::WriteAllText((Join-Path $fixture 'com.vercel.cli/Data/auth.json'), '{"token":"synthetic-token-not-a-credential"}')
$null = & pwsh -NoProfile -File $helper -Action Prepare -StoreDirectory $store
if ($LASTEXITCODE -ne 0) { throw 'Fixture custody failed' }
$mockFile = Join-Path $fixture 'mock-transfer.ps1'
@'
param($Helper, $Store, $Fixture, $Mode)
$env:APPDATA = $Fixture
function Invoke-RestMethod {
    param($Uri, $Headers, $Method, $ContentType, $Body, $TimeoutSec)
    if ($Method -ne 'Post') { return [pscustomobject]@{id='prj_5OMS0EPuwS3cuQF4ICqBYBF6UNlT'; name='siii'; accountId='team_yJjgpau6x2SsEeKXBw09sE4a'} }
    $request = $Body | ConvertFrom-Json
    if ($request.type -ne 'sensitive' -or $request.visibility -ne 'secret' -or @($request.target).Count -ne 1 -or $request.target[0] -ne 'production') { throw 'Unsafe request' }
    $result = [pscustomobject]@{created=[pscustomobject]@{key=$request.key;type='sensitive';target=@('production');visibility='secret'};failed=@()}
    if ($Mode -eq 'application-failure') {$result.failed=@(@{error=@{code='synthetic-failure'}})}
    if ($Mode -eq 'wrong-target') {$result.created.target=@('preview')}
    if ($Mode -eq 'readable-value') {$result.created.visibility='config'}
    if ($Mode -eq 'missing-created') {$result.created=$null}
    return $result
}
& $Helper -Action ConfigureSiii -StoreDirectory $Store
exit $LASTEXITCODE
'@ | Set-Content -LiteralPath $mockFile
foreach ($mode in @('success', 'application-failure', 'wrong-target', 'readable-value', 'missing-created')) {
    $output = & pwsh -NoProfile -File $mockFile $helper $store $fixture $mode
    if ($output -match 'secretBase64|SECRET_BASE64=|Bearer |synthetic-token') { throw 'Sensitive output' }
    if (($LASTEXITCODE -eq 0) -ne ($mode -eq 'success')) { Write-Output $output; throw "Transfer result mismatch: $mode" }
}
Write-Output 'PASS: mocked transfer certifies production Secret and rejects 2xx failures, wrong target, readable visibility and missing creation. No real network call.'
exit 0
