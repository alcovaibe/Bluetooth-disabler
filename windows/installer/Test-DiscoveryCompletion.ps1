$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Wait-DiscoveryCompletion.ps1')
function State([long]$Revision, [bool]$Ready, [string]$Error = '') {
    [pscustomobject]@{ Revision = $Revision; CanRefresh = $Ready; Error = $Error }
}

# Old ready state must not be accepted; even a new revision is insufficient
# while the refresh button is disabled.
$script:states = @((State 1 $true), (State 2 $false), (State 2 $true))
$script:reads = 0
$revision = Wait-DiscoveryCompletion -AfterRevision 1 -PollIntervalMilliseconds 1 -ReadState {
    $state = $script:states[[Math]::Min($script:reads, $script:states.Count - 1)]
    $script:reads++
    $state
}
if ($revision -ne 2 -or $script:reads -ne 3) { throw 'Accepted a stale or unfinished refresh.' }
Write-Host 'Passed: waits for a new completed refresh.'

# A missed F5 must time out, not report the previously completed discovery.
$failure = $null
try {
    $null = Wait-DiscoveryCompletion -AfterRevision 1 -TimeoutMilliseconds 20 -PollIntervalMilliseconds 1 -ReadState { State 1 $true }
} catch { $failure = $_.Exception.Message }
if (!$failure -or !$failure.Contains('No new completed discovery')) { throw 'Missed F5 was not rejected.' }
Write-Host 'Passed: a missed F5 fails verification.'

$failure = $null
try {
    $null = Wait-DiscoveryCompletion -AfterRevision 1 -ReadState { State 2 $true 'API failure' }
} catch { $failure = $_.Exception.Message }
if (!$failure -or !$failure.Contains('Discovery failed: API failure')) { throw 'Discovery error was not rejected.' }
Write-Host 'Passed: discovery failures cannot report success.'

if ((Wait-DiscoveryCompletion -ReadState { State 1 $true }) -ne 1) { throw 'Initial discovery did not complete.' }
Write-Host 'Passed: initial discovery waits for its first completed revision.'
