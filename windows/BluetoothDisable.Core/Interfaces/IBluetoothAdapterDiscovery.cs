using BluetoothDisable.Core.Models;

namespace BluetoothDisable.Core.Interfaces;

/// <summary>
/// Read-only boundary for future Windows device enumeration. Include disabled
/// radios so they can be restored; do not rely only on active radio interfaces.
/// Unknown or ambiguous devices must not be classified as physical radios.
/// </summary>
public interface IBluetoothAdapterDiscovery
{
    Task<IReadOnlyList<BluetoothAdapter>> GetAdaptersAsync(
        CancellationToken cancellationToken = default);
}
