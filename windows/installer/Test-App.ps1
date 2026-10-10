param(
    [Parameter(Mandatory)][string]$Executable,
    [string]$ReportDirectory = (Join-Path $PSScriptRoot '../TestResults/ui')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-DiscoveryCompletion.ps1')
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing, System.Windows.Forms
New-Item -ItemType Directory -Force $ReportDirectory | Out-Null
$process = Start-Process -FilePath (Resolve-Path $Executable).Path -WindowStyle Hidden -PassThru
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 200
        $process.Refresh()
        if ($process.HasExited) { throw "Application exited early: $($process.ExitCode)" }
    } until ($process.MainWindowHandle -ne 0 -or [DateTime]::UtcNow -gt $deadline)
    if (!$process.MainWindowHandle) { throw 'No application window.' }
    $window = [Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
    $scope = [Windows.Automation.TreeScope]::Descendants
    function Elements { $window.FindAll($scope, [Windows.Automation.Condition]::TrueCondition) }
    function Names { @(Elements | ForEach-Object { $_.Current.Name }) }
    function Await-Discovery([long]$AfterRevision = 0) {
        Wait-DiscoveryCompletion -AfterRevision $AfterRevision -ReadState {
            $names = Names
            $refreshControl = $window.FindFirst($scope, [Windows.Automation.PropertyCondition]::new(
                [Windows.Automation.AutomationElement]::AutomationIdProperty, 'RefreshAdapters'))
            if (!$refreshControl) { return $null }
            $revision = 0L
            if (![long]::TryParse($refreshControl.Current.ItemStatus, [ref]$revision)) { return $null }
            [pscustomobject]@{
                Revision = $revision
                CanRefresh = $refreshControl.Current.IsEnabled
                Error = $(if ($names -contains 'Ошибка обнаружения') { $names -join ' | ' } else { '' })
            }
        }
    }
    $revision = Await-Discovery
    $refresh = $window.FindFirst($scope, [Windows.Automation.PropertyCondition]::new(
        [Windows.Automation.AutomationElement]::AutomationIdProperty, 'RefreshAdapters'))
    if (!$refresh.Current.IsKeyboardFocusable) { throw 'Refresh is not keyboard accessible.' }
    $invoke = $refresh.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern)
    $invoke.Invoke()
    $revision = Await-Discovery -AfterRevision $revision
    $items = @(Elements | Where-Object { $_.Current.ControlType -eq [Windows.Automation.ControlType]::ListItem })
    @(Elements | ForEach-Object { [ordered]@{ name=$_.Current.Name; type=$_.Current.ControlType.ProgrammaticName; enabled=$_.Current.IsEnabled; offscreen=$_.Current.IsOffscreen } }) |
        ConvertTo-Json -Depth 4 | Set-Content (Join-Path $ReportDirectory 'elements.json') -Encoding utf8
    if ($items.Count -gt 0) {
        $items[0].SetFocus()
        $selection = $items[0].GetCurrentPattern([Windows.Automation.SelectionItemPattern]::Pattern)
        $selection.Select()
        if (!$selection.Current.IsSelected) { throw 'Adapter selection failed.' }
    }
    $refresh.SetFocus()
    if (![Windows.Automation.AutomationElement]::FocusedElement.Current.ProcessId.Equals($process.Id)) { throw 'Keyboard focus is outside the application.' }
    [Windows.Forms.SendKeys]::SendWait('{TAB}')
    Start-Sleep -Milliseconds 100
    if (![Windows.Automation.AutomationElement]::FocusedElement.Current.ProcessId.Equals($process.Id)) { throw 'Tab navigation left the application.' }
    [Windows.Forms.SendKeys]::SendWait('{F5}')
    $revision = Await-Discovery -AfterRevision $revision
    foreach ($name in @('Заблокировать Bluetooth','Включить адаптер')) {
        $button = $window.FindFirst($scope, [Windows.Automation.AndCondition]::new(
            [Windows.Automation.PropertyCondition]::new([Windows.Automation.AutomationElement]::NameProperty,$name),
            [Windows.Automation.PropertyCondition]::new([Windows.Automation.AutomationElement]::ControlTypeProperty,[Windows.Automation.ControlType]::Button)))
        if (!$button -or $button.Current.IsEnabled) { throw "Future hardware action must be disabled: $name" }
    }
    # Read the UI at narrow and normal sizes; no system DPI/display settings are changed.
    $transform = $window.GetCurrentPattern([Windows.Automation.TransformPattern]::Pattern)
    foreach ($size in @(@(560,650),@(900,850))) {
        $transform.Resize($size[0],$size[1])
        Start-Sleep -Milliseconds 250
        if (!$process.Responding) { throw 'Window stopped responding after resize.' }
    }
    $bounds = $window.Current.BoundingRectangle
    $image = [Drawing.Bitmap]::new([int]$bounds.Width,[int]$bounds.Height)
    $graphics = [Drawing.Graphics]::FromImage($image)
    try {
        $graphics.CopyFromScreen([int]$bounds.Left,[int]$bounds.Top,0,0,$image.Size)
        $image.Save((Join-Path $ReportDirectory 'window.png'),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $image.Dispose() }
    [ordered]@{ executable=$Executable; title=$window.Current.Name; listItems=$items.Count;
        refreshed=$true; selection=($items.Count -gt 0); keyboardTabAndF5=$true; disabledActions=$true; resized=$true;
        completedRefreshRevision=$revision;
        uiText=@(Names) } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $ReportDirectory 'ui.json') -Encoding utf8
} finally {
    $process.Refresh()
    if (!$process.HasExited) {
        $null = $process.CloseMainWindow()
        if (!$process.WaitForExit(10000)) { throw 'Application did not close normally.' }
    }
}
if ($process.ExitCode -ne 0) { throw "Application exit code: $($process.ExitCode)" }
Write-Host 'UI smoke passed; this does not replace tests at other DPI settings or on other hardware.'
