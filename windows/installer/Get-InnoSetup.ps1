# Build-only dependency. Never bundled with Bluetooth Disable.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$tools = Join-Path $PSScriptRoot '../artifacts/tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$setup = Join-Path $tools 'innosetup-6.7.3.exe'
$expected = '9C73C3BAE7ED48D44112A0F48E66742C00090BDB5BEF71D9D3C056C66E97B732'
if (!(Test-Path -LiteralPath $setup)) {
    # Both download locations are maintained by Inno Setup. Keep the pinned
    # version, SHA-256 and Authenticode checks mandatory on all mirrors.
    $sources = @(
        'https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe',
        'https://files.jrsoftware.org/is/6/innosetup-6.7.3.exe'
    )
    $downloaded = $false
    foreach ($url in $sources) {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            $candidate = Join-Path $tools ('innosetup-download-' + [guid]::NewGuid().ToString('N') + '.exe')
            try {
                Invoke-WebRequest -Uri $url -OutFile $candidate -TimeoutSec 90 -ErrorAction Stop
                if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $expected) {
                    throw 'Unexpected Inno Setup SHA256.'
                }
                $candidateSignature = Get-AuthenticodeSignature -LiteralPath $candidate
                if ($candidateSignature.Status -ne 'Valid' -or $candidateSignature.SignerCertificate.Subject -notmatch 'CN=Pyrsys B.V.,') {
                    throw 'Inno Setup signature verification failed.'
                }
                Move-Item -LiteralPath $candidate -Destination $setup -ErrorAction Stop
                $downloaded = $true
                break
            } catch {
                if ($_.Exception.Message -match '^(Unexpected Inno Setup SHA256|Inno Setup signature verification failed)') {
                    throw
                }
                Write-Warning "Inno Setup download failed from $url (attempt $attempt of 3)."
                if ($attempt -lt 3) { Start-Sleep -Seconds (5 * $attempt) }
            } finally {
                if (Test-Path -LiteralPath $candidate) { Remove-Item -LiteralPath $candidate -Force }
            }
        }
        if ($downloaded) { break }
    }
    if (!$downloaded) { throw 'Cannot download verified Inno Setup 6.7.3 from either official source.' }
}
if ((Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash -ne $expected) { throw 'Unexpected Inno Setup SHA256.' }
$signature = Get-AuthenticodeSignature -LiteralPath $setup
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'CN=Pyrsys B.V.,') {
    throw 'Inno Setup signature verification failed.'
}
# Cache only the verified installer, never an executable tool directory. A fresh
# directory also prevents stale/replaced compiler DLLs or resources being reused.
$destination = [IO.Path]::GetFullPath((Join-Path $tools ('InnoSetup-' + [guid]::NewGuid().ToString('N'))))
$compiler = Join-Path $destination 'ISCC.exe'
$process = Start-Process -FilePath $setup -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART',
    '/CURRENTUSER', '/NOICONS', '/TASKS=', ('/DIR="' + $destination + '"')) -WindowStyle Hidden -PassThru -Wait
if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $compiler)) { throw "Inno Setup installation failed: $($process.ExitCode)" }
# The exact distribution/version is established by the pinned installer hash;
# ISCC's own ProductVersion metadata is not the compiler engine version.
(Resolve-Path -LiteralPath $compiler).Path
