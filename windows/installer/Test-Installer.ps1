[CmdletBinding()]
param([string]$Installer)
$ErrorActionPreference = 'Stop'
$windowsRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$Installer) { $Installer = (Get-Content "$windowsRoot/artifacts/installer/build-info.json" -Raw | ConvertFrom-Json).installer }
$registration = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{28D89097-25E5-465E-99E4-07DD9F74BDB0}_is1'
$shortcutFolder = Join-Path ([Environment]::GetFolderPath('Programs')) 'Bluetooth Disable'
if ((Test-Path $registration) -or (Test-Path -LiteralPath $shortcutFolder)) {
    throw 'An existing EXE installation/shortcut was found. Test only in a clean user profile; it will not be overwritten.'
}
$testRoot = [IO.Path]::GetFullPath((Join-Path $windowsRoot 'artifacts/install-smoke'))
$target = [IO.Path]::GetFullPath((Join-Path $testRoot ([guid]::NewGuid().ToString('N'))))
if (!$target.StartsWith($testRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test path.' }
$report = New-Item -ItemType Directory -Force "$windowsRoot/TestResults/installer"
$app = $null
try {
    foreach ($attempt in 1..2) {
        $setup = Start-Process -FilePath (Resolve-Path $Installer).Path -WindowStyle Hidden -PassThru -Wait -ArgumentList @(
            '/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART',('/DIR="' + $target + '"'),
            ('/LOG="' + $report.FullName + '/install-' + $attempt + '.log"'))
        if ($setup.ExitCode -ne 0) { throw "Install/reinstall failed: $($setup.ExitCode)" }
        $entry = Get-ItemProperty $registration
        if ($entry.DisplayName -ne 'Bluetooth Disable' -or $entry.InstallLocation.TrimEnd('\') -ne $target) {
            throw 'Incorrect Windows uninstall registration.'
        }
        if (!(Test-Path -LiteralPath (Join-Path $shortcutFolder 'Bluetooth Disable.lnk'))) { throw 'Start menu shortcut is missing.' }
    }
    $app = Start-Process -FilePath (Join-Path $target 'BluetoothDisable.App.exe') -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 250
        $app.Refresh()
        if ($app.HasExited) { throw "Installed application exited early: $($app.ExitCode)" }
    } until ($app.MainWindowHandle -ne 0 -or [DateTime]::UtcNow -gt $deadline)
    if ($app.MainWindowHandle -eq 0 -or !$app.Responding -or $app.MainWindowTitle -ne 'Bluetooth Disable') {
        throw 'Installed WinUI window failed its smoke test.'
    }
    Start-Sleep -Seconds 2
    $null = $app.CloseMainWindow()
    if (!$app.WaitForExit(10000) -or $app.ExitCode -ne 0) { throw 'Installed app did not close normally.' }
} finally {
    if ($app -and !$app.HasExited) { $null = $app.CloseMainWindow(); $null = $app.WaitForExit(10000) }
    $uninstaller = Join-Path $target 'unins000.exe'
    if (Test-Path -LiteralPath $uninstaller) {
        if (Test-Path $registration) {
            if ((Get-ItemProperty $registration).InstallLocation.TrimEnd('\') -ne $target) { throw 'Refusing to uninstall a different installation.' }
        }
        $uninstall = Start-Process -FilePath $uninstaller -WindowStyle Hidden -PassThru -Wait -ArgumentList @(
            '/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART',('/LOG="' + $report.FullName + '/uninstall.log"'))
        if ($uninstall.ExitCode -ne 0) { throw "Uninstall failed: $($uninstall.ExitCode)" }
    }
}
if ((Test-Path $registration) -or (Test-Path -LiteralPath (Join-Path $target 'BluetoothDisable.App.exe')) -or
    (Test-Path -LiteralPath (Join-Path $shortcutFolder 'Bluetooth Disable.lnk'))) { throw 'Uninstall left application registration/files/shortcut.' }
[ordered]@{ installer=$Installer; install=$true; sameVersionReinstall=$true; windowLaunch=$true; cleanUninstall=$true; target=$target } |
    ConvertTo-Json | Set-Content "$windowsRoot/TestResults/installer/result.json" -Encoding utf8
Write-Host 'Per-user install, same-version reinstall, window launch and uninstall passed.'