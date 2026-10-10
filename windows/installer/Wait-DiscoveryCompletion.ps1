function Wait-DiscoveryCompletion {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][scriptblock]$ReadState,
        [long]$AfterRevision = 0,
        [ValidateRange(1, 60000)][int]$TimeoutMilliseconds = 20000,
        [ValidateRange(1, 1000)][int]$PollIntervalMilliseconds = 100
    )
    $clock = [Diagnostics.Stopwatch]::StartNew()
    do {
        $state = & $ReadState
        if ($null -ne $state) {
            if ($state.Error) { throw "Discovery failed: $($state.Error)" }
            if ($state.CanRefresh -and $state.Revision -gt $AfterRevision) {
                return [long]$state.Revision
            }
        }
        if ($clock.ElapsedMilliseconds -ge $TimeoutMilliseconds) { break }
        Start-Sleep -Milliseconds $PollIntervalMilliseconds
    } while ($true)
    throw "No new completed discovery after revision $AfterRevision."
}
