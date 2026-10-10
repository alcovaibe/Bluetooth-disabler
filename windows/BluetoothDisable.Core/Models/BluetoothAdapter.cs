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
        bool isPresent = false,
        BluetoothRadioState radioState = BluetoothRadioState.Unknown,
        uint? deviceProblemCode = null,
        string? observationNote = null)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(deviceInstanceId);
        ArgumentException.ThrowIfNullOrWhiteSpace(displayName);

        DeviceInstanceId = deviceInstanceId;
        DisplayName = displayName;
        Kind = kind;
        State = state;
        IsPresent = isPresent;
        RadioState = radioState;
        DeviceProblemCode = deviceProblemCode;
        ObservationNote = observationNote;
    }

    /// <summary>Windows device instance ID; do not persist a transient DEVINST handle.</summary>
    public string DeviceInstanceId { get; }
    public string DisplayName { get; }
    public BluetoothAdapterKind Kind { get; }
    public BluetoothAdapterState State { get; }
    public bool IsPresent { get; }
    /// <summary>Radio power is independent of the Device Manager devnode state.</summary>
    public BluetoothRadioState RadioState { get; }
    public uint? DeviceProblemCode { get; }
    public string? ObservationNote { get; }
}

public enum BluetoothRadioState { Unknown, On, Off, Disabled }

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
