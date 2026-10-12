namespace BluetoothDisable.Core.Models;

/// <summary>
/// Result for one adapter, allowing partial success in a multi-adapter operation.
/// ConfigurationManagerError is a CONFIGRET value, not a Win32 last-error code.
/// Success must be reported only after verifying the resulting device state.
/// </summary>
public sealed record AdapterOperationResult(
    string DeviceInstanceId,
    AdapterOperation Operation,
    AdapterOperationStatus Status,
    uint? ConfigurationManagerError = null,
    bool RequiresRestart = false,
    string? Message = null)
{
    public bool IsSuccess => Status == AdapterOperationStatus.Succeeded;
}

public enum AdapterOperation
{
    Disable,
    Enable
}

public enum AdapterOperationStatus
{
    Unknown,
    Succeeded,
    AccessDenied,
    DeviceNotFound,
    UnsafeDevice,
    NotSupported,
    Failed
}
