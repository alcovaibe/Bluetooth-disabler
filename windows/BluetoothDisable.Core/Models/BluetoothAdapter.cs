namespace BluetoothDisable.Core.Models;

/// <summary>
/// A discovery snapshot. PhysicalRadio must be established from device properties
/// and topology by the Windows implementation, never from a display name alone.
/// </summary>
public sealed record BluetoothAdapter
{
    public BluetoothAdapter(
        string deviceInstanceId,
        string displayName,
        BluetoothAdapterKind kind = BluetoothAdapterKind.Unknown,
        BluetoothAdapterState state = BluetoothAdapterState.Unknown,
        bool isPresent = false)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(deviceInstanceId);
        ArgumentException.ThrowIfNullOrWhiteSpace(displayName);

        DeviceInstanceId = deviceInstanceId;
        DisplayName = displayName;
        Kind = kind;
        State = state;
        IsPresent = isPresent;
    }

    /// <summary>Windows device instance ID; do not persist a transient DEVINST handle.</summary>
    public string DeviceInstanceId { get; }
    public string DisplayName { get; }
    public BluetoothAdapterKind Kind { get; }
    public BluetoothAdapterState State { get; }
    public bool IsPresent { get; }
}

public enum BluetoothAdapterKind
{
    Unknown,
    PhysicalRadio,
    VirtualDevice,
    Peripheral
}

public enum BluetoothAdapterState
{
    Unknown,
    Enabled,
    Disabled
}
