using BluetoothDisable.Core.Interfaces;
using BluetoothDisable.Core.Models;

namespace BluetoothDisable.Tests.Fakes;

internal sealed class FakeBluetoothAdapterDiscovery : IBluetoothAdapterDiscovery
{
    public IReadOnlyList<BluetoothAdapter> Adapters { get; init; } = [];
    public Exception? Failure { get; init; }
    public int CallCount { get; private set; }
    public CancellationToken LastCancellationToken { get; private set; }

    public Task<IReadOnlyList<BluetoothAdapter>> GetAdaptersAsync(CancellationToken cancellationToken = default)
    {
        CallCount++;
        LastCancellationToken = cancellationToken;
        cancellationToken.ThrowIfCancellationRequested();

        return Failure is { } failure
            ? Task.FromException<IReadOnlyList<BluetoothAdapter>>(failure)
            : Task.FromResult(Adapters);
    }
}
