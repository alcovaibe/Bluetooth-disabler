# Build-only dependency. Never bundled with Bluetooth Disable.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$tools = Join-Path $PSScriptRoot '../artifacts/tools'
$compiler = Join-Path $tools 'InnoSetup/ISCC.exe'
if (Test-Path -LiteralPath $compiler) { return (Resolve-Path -LiteralPath $compiler).Path }
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$setup = Join-Path $tools 'innosetup-6.7.3.exe'
if (!(Test-Path -LiteralPath $setup)) {
    Invoke-WebRequest 'https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe' -OutFile $setup
}
$expected = '9C73C3BAE7ED48D44112A0F48E66742C00090BDB5BEF71D9D3C056C66E97B732'
if ((Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash -ne $expected) { throw 'Unexpected Inno Setup SHA256.' }
$signature = Get-AuthenticodeSignature -LiteralPath $setup
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'CN=Pyrsys B.V.,') {
    throw 'Inno Setup signature verification failed.'
}
$destination = [IO.Path]::GetFullPath((Join-Path $tools 'InnoSetup'))
$process = Start-Process -FilePath $setup -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART',
    '/CURRENTUSER', '/NOICONS', '/TASKS=', ('/DIR="' + $destination + '"')) -WindowStyle Hidden -PassThru -Wait
if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $compiler)) { throw "Inno Setup installation failed: $($process.ExitCode)" }
(Resolve-Path -LiteralPath $compiler).Path