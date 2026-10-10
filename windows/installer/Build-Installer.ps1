[CmdletBinding()]
param([string]$InnoCompiler)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$windowsRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$output = Join-Path $windowsRoot 'artifacts/installer'
$work = Join-Path $windowsRoot ('artifacts/build/' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $work 'publish'
New-Item -ItemType Directory -Force -Path $output,$work | Out-Null
if (!$InnoCompiler) { $InnoCompiler = & "$PSScriptRoot/Get-InnoSetup.ps1" }
else {
    $InnoCompiler = (Resolve-Path -LiteralPath $InnoCompiler).Path
    Write-Warning 'Explicit -InnoCompiler override: you are trusting this local compiler and its resources. The pinned installer verification is bypassed.'
}
$msixTools = @()
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
if (Test-Path -LiteralPath $vswhere) {
    $pdbTool = & $vswhere -latest -products '*' -find 'VC\Tools\MSVC\**\bin\Hostx64\x64\mspdbcmf.exe' | Select-Object -First 1
    if ($pdbTool -and (Test-Path -LiteralPath $pdbTool)) { $msixTools += "-p:MsPdbCmfExeFullpath=$pdbTool" }
}
[xml]$project = Get-Content "$windowsRoot/BluetoothDisable.App/BluetoothDisable.App.csproj"
$version = $project.SelectSingleNode('/Project/PropertyGroup/Version').InnerText
[xml]$manifest = Get-Content "$windowsRoot/BluetoothDisable.App/Package.appxmanifest"
if ($manifest.Package.Identity.Version -ne "$version.0") { throw 'EXE and MSIX versions must agree.' }
Push-Location $windowsRoot
try {
    dotnet publish BluetoothDisable.App/BluetoothDisable.App.csproj -c Release -r win-x64 -p:Platform=x64 --self-contained true -p:WindowsPackageType=None -p:WindowsAppSDKSelfContained=true -p:AppxPackageSigningEnabled=false -p:GenerateAppxPackageOnBuild=false --artifacts-path "$work/unpackaged" --output $payload --verbosity minimal "-bl:$work/publish.binlog"
    if ($LASTEXITCODE -ne 0) { throw "Self-contained publish failed: $LASTEXITCODE" }
    foreach ($file in @('BluetoothDisable.App.exe','coreclr.dll','Microsoft.UI.Xaml.dll','BluetoothDisable.App.pri','Assets/AppIcon.ico')) {
        if (!(Test-Path -LiteralPath (Join-Path $payload $file))) { throw "Missing payload file: $file" }
    }
    if (Get-ChildItem $payload -Recurse -Include *.pfx,*.p12,*.pvk,*.jks,*.apk,*.aab,*.cs,*.csproj) {
        throw 'Unexpected source, Android or private signing material in installer payload.'
    }
    Copy-Item "$windowsRoot/../LICENSE" "$payload/LICENSE.txt"
    Copy-Item "$PSScriptRoot/THIRD-PARTY-NOTICES.md" "$payload/THIRD-PARTY-NOTICES.md"
    # Include the exact restored package notices/license files (no keys, source or build tools).
    $licenses = New-Item -ItemType Directory -Force "$payload/licenses"
    $assetsFile = Get-ChildItem "$work/unpackaged/obj/BluetoothDisable.App" -Filter project.assets.json -Recurse | Select-Object -First 1
    $assets = Get-Content $assetsFile.FullName -Raw | ConvertFrom-Json
    foreach ($library in $assets.libraries.PSObject.Properties) {
        if ($library.Value.type -ne 'package') { continue }
        foreach ($packageFolder in $assets.packageFolders.PSObject.Properties.Name) {
            $packagePath = Join-Path $packageFolder $library.Value.path
            if (!(Test-Path -LiteralPath $packagePath)) { continue }
            $destination = Join-Path $licenses.FullName ($library.Name -replace '/', '-')
            New-Item -ItemType Directory -Force $destination | Out-Null
            Get-ChildItem $packagePath -File | Where-Object { $_.Name -match '^(license|notice|third.party)' -or $_.Extension -eq '.nuspec' } |
                Copy-Item -Destination $destination
        }
    }
    # Runtime packs are SDK download dependencies, not normal PackageReference entries.
    $runtimeConfig = Get-Content "$payload/BluetoothDisable.App.runtimeconfig.json" -Raw | ConvertFrom-Json
    $runtimeVersion = ($runtimeConfig.runtimeOptions.includedFrameworks | Where-Object name -EQ 'Microsoft.NETCore.App').version
    $runtimePackage = $assets.packageFolders.PSObject.Properties.Name | ForEach-Object {
        Join-Path $_ "microsoft.netcore.app.runtime.win-x64/$runtimeVersion"
    } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (!$runtimePackage) { throw 'Cannot locate the published .NET runtime license notices.' }
    $runtimeNotices = New-Item -ItemType Directory -Force "$payload/licenses/dotnet-runtime-$runtimeVersion"
    Copy-Item "$runtimePackage/LICENSE.TXT","$runtimePackage/THIRD-PARTY-NOTICES.TXT" $runtimeNotices.FullName
    $innoNotices = New-Item -ItemType Directory -Force "$payload/licenses/InnoSetup"
    Copy-Item (Join-Path (Split-Path $InnoCompiler) 'License.txt') $innoNotices.FullName
    & $InnoCompiler /Qp "/DPayloadDir=$payload" "/DOutputDir=$output" "/DAppVersion=$version" "$PSScriptRoot/BluetoothDisable.iss"
    if ($LASTEXITCODE -ne 0) { throw "EXE installer compilation failed: $LASTEXITCODE" }
    # Build the original single-project MSIX separately; no certificate or trust-store mutation.
    dotnet publish BluetoothDisable.App/BluetoothDisable.App.csproj -c Release -r win-x64 -p:Platform=x64 --self-contained true -p:WindowsAppSDKSelfContained=true -p:AppxPackageSigningEnabled=false -p:GenerateAppxPackageOnBuild=true -p:AppxBundle=Never -p:UapAppxPackageBuildMode=SideloadOnly "-p:PackageLicenseDirectory=$payload/licenses" "-p:AppxPackageDir=$work/msix/" --artifacts-path "$work/packaged" --verbosity minimal "-bl:$work/msix.binlog" @msixTools
    if ($LASTEXITCODE -ne 0) { throw "MSIX packaging failed: $LASTEXITCODE" }
    $generatedMsix = @(Get-ChildItem "$work/msix" -Recurse -Filter *.msix)
    if ($generatedMsix.Count -eq 0) { throw 'MSIX tooling did not produce a package in this build.' }
    New-Item -ItemType Directory -Force "$output/msix" | Out-Null
    $msix = @($generatedMsix | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination "$output/msix" -PassThru })
    $exe = Join-Path $output "BluetoothDisable-$version-win-x64-setup.exe"
    Get-FileHash -Algorithm SHA256 -LiteralPath $exe | Format-List
    [ordered]@{ version=$version; installer=$exe; payload=$payload; msix=@($msix.FullName); signed=$false; buildDirectory=$work } |
        ConvertTo-Json | Set-Content "$output/build-info.json" -Encoding utf8
    Write-Host "Installer: $exe"
    Write-Host "Unsigned MSIX: $($msix.FullName -join ', ')"
} finally { Pop-Location }
