# Regression tests: an existing compiler must not bypass installer verification.
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('bd-inno-test-' + [guid]::NewGuid().ToString('N'))
try {
    $scripts = New-Item -ItemType Directory -Force (Join-Path $testRoot 'installer')
    $tools = New-Item -ItemType Directory -Force (Join-Path $testRoot 'artifacts/tools/InnoSetup')
    Copy-Item (Join-Path $PSScriptRoot 'Get-InnoSetup.ps1') $scripts.FullName
    Set-Content (Join-Path $tools.FullName 'ISCC.exe') 'untrusted cached compiler'
    Set-Content (Join-Path $testRoot 'artifacts/tools/innosetup-6.7.3.exe') 'untrusted installer'
    $expectedHash = '9C73C3BAE7ED48D44112A0F48E66742C00090BDB5BEF71D9D3C056C66E97B732'
    foreach ($case in @('hash', 'signature', 'publisher')) {
        $executed = $false
        function Get-FileHash {
            param($LiteralPath, $Algorithm)
            if ($case -eq 'hash') { Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm }
            else { [pscustomobject]@{ Hash = $expectedHash } }
        }
        function Get-AuthenticodeSignature {
            param($LiteralPath)
            [pscustomobject]@{
                Status = $(if ($case -eq 'signature') { 'NotSigned' } else { 'Valid' })
                SignerCertificate = [pscustomobject]@{ Subject = 'CN=Unexpected publisher' }
            }
        }
        function Start-Process { $script:executed = $true; throw 'Untrusted installer executed.' }
        $failure = $null
        try { $null = & (Join-Path $scripts.FullName 'Get-InnoSetup.ps1') }
        catch { $failure = $_.Exception.Message }
        $expectedFailure = if ($case -eq 'hash') { 'Unexpected Inno Setup SHA256' } else { 'Inno Setup signature verification failed' }
        if (!$failure -or !$failure.Contains($expectedFailure) -or $executed) {
            throw "Verification regression ($case): $failure"
        }
        Write-Host "Passed: cached compiler cannot bypass $case verification."
    }
} finally {
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
