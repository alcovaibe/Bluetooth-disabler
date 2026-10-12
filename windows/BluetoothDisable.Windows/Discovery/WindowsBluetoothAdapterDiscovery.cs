using System.Runtime.InteropServices;
using BluetoothDisable.Core.Interfaces;
using BluetoothDisable.Core.Models;
using Windows.Devices.Enumeration;
using Windows.Devices.Radios;
using NativeAdapter = Windows.Devices.Bluetooth.BluetoothAdapter;
using Adapter = BluetoothDisable.Core.Models.BluetoothAdapter;

namespace BluetoothDisable.Windows.Discovery;

/// <summary>Local host radios only. All native/WinRT calls here are read-only.</summary>
public sealed class WindowsBluetoothAdapterDiscovery : IBluetoothAdapterDiscovery
{
    public async Task<IReadOnlyList<Adapter>> GetAdaptersAsync(CancellationToken cancellationToken = default)
    {
        var snapshots = await Task.Run(() => SetupApi.Read(cancellationToken), cancellationToken).ConfigureAwait(false);
        var physical = snapshots.Where(item => item.IsPhysicalRadio).ToArray();
        if (physical.Length == 0) return [];

        var states = new Dictionary<string, BluetoothRadioState>(StringComparer.OrdinalIgnoreCase);
        string? radioNote = null;
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(10));
        try
        {
            var interfaces = await DeviceInformation.FindAllAsync(NativeAdapter.GetDeviceSelector(),
                ["System.Devices.DeviceInstanceId"]).AsTask(timeout.Token).ConfigureAwait(false);
            foreach (var device in interfaces)
            {
                if (!device.Properties.TryGetValue("System.Devices.DeviceInstanceId", out var value) || value is not string id)
                    continue; // Never correlate radios by display name or enumeration order.
                if (!physical.Any(item => item.InstanceId.Equals(id, StringComparison.OrdinalIgnoreCase))) continue;
                var adapter = await NativeAdapter.FromIdAsync(device.Id).AsTask(timeout.Token).ConfigureAwait(false);
                var radio = adapter is null ? null : await adapter.GetRadioAsync().AsTask(timeout.Token).ConfigureAwait(false);
                var state = radio?.State switch
                {
                    RadioState.On => BluetoothRadioState.On,
                    RadioState.Off => BluetoothRadioState.Off,
                    RadioState.Disabled => BluetoothRadioState.Disabled,
                    _ => BluetoothRadioState.Unknown
                };
                states[id] = states.TryGetValue(id, out var old) && old != state ? BluetoothRadioState.Unknown : state;
            }
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            states.Clear();
            radioNote = "Windows не ответила на запрос состояния радиомодуля за 10 секунд.";
        }
        catch (Exception ex) when (ex is COMException or UnauthorizedAccessException)
        {
            states.Clear();
            radioNote = $"Состояние радиомодуля недоступно (0x{ex.HResult:X8}).";
        }
        cancellationToken.ThrowIfCancellationRequested();
        return physical.Select(item => new Adapter(item.InstanceId, item.Name, BluetoothAdapterKind.PhysicalRadio,
            item.DeviceState, true, states.GetValueOrDefault(item.InstanceId, BluetoothRadioState.Unknown),
            item.StatusResult == 0 && (item.Status & 0x400) != 0 ? item.Problem : null,
            item.StatusResult != 0 ? $"Состояние устройства недоступно: CONFIGRET=0x{item.StatusResult:X}."
                : radioNote ?? (!states.ContainsKey(item.InstanceId) ? "Windows не предоставила состояние радиомодуля." : null)))
            .ToArray();
    }
}
