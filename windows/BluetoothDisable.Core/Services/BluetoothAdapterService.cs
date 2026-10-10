using BluetoothDisable.Core.Interfaces;
using BluetoothDisable.Core.Models;

namespace BluetoothDisable.Core.Services;

/// <summary>
/// Selects candidates for display and future operations without changing hardware.
/// This filter is not a security boundary; the elevated controller must revalidate.
/// Discovery failures propagate to the caller, rather than appearing as an empty list.
/// </summary>
public sealed class BluetoothAdapterService
{
    private readonly IBluetoothAdapterDiscovery _discovery;

    public BluetoothAdapterService(IBluetoothAdapterDiscovery discovery)
    {
        ArgumentNullException.ThrowIfNull(discovery);
        _discovery = discovery;
    }

    public async Task<IReadOnlyList<BluetoothAdapter>> GetPhysicalAdaptersAsync(
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var adapters = await _discovery.GetAdaptersAsync(cancellationToken).ConfigureAwait(false);
        cancellationToken.ThrowIfCancellationRequested();

        return adapters
            .GroupBy(adapter => adapter.DeviceInstanceId, StringComparer.OrdinalIgnoreCase)
            // Reject the whole identity if duplicate discovery records disagree on eligibility.
            .Where(group => group.All(adapter =>
                adapter.Kind == BluetoothAdapterKind.PhysicalRadio && adapter.IsPresent))
            .Select(group => group.First())
            .ToArray();
    }
}
