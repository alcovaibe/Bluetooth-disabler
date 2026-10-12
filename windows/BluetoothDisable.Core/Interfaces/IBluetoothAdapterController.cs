using BluetoothDisable.Core.Models;

namespace BluetoothDisable.Core.Interfaces;

/// <summary>
/// Boundary for future elevated Windows operations; no native implementation exists yet.
/// Re-resolve the instance ID and independently verify a physical Bluetooth radio
/// immediately before each mutation, including requests from an unprivileged UI.
/// Never trust a cached snapshot or accept arbitrary device nodes from the caller.
/// Return expected device/access failures as results. Cancellation uses
/// OperationCanceledException and does not imply rollback of an already applied change.
/// </summary>
public interface IBluetoothAdapterController
{
    /// <summary>
    /// AcrossRestarts maps to CM_DISABLE_PERSIST. The implementation must verify
    /// the post-operation state and preserve sufficient recovery information.
    /// </summary>
    Task<AdapterOperationResult> DisableAsync(
        string deviceInstanceId,
        DisablePersistence persistence,
        CancellationToken cancellationToken = default);

    /// <summary>Restore one verified radio using CM_Enable_DevNode.</summary>
    Task<AdapterOperationResult> EnableAsync(
        string deviceInstanceId,
        CancellationToken cancellationToken = default);
}
