using BluetoothDisable.Core.Models;

namespace BluetoothDisable.Windows.Discovery;

// Read-only evidence. This classifier does not authorize future hardware changes.
internal sealed record PnpRadioSnapshot(string InstanceId, string Name, string Service,
    string Enumerator, string[] HardwareIds, string[] CompatibleIds, string? ParentId,
    uint StatusResult, uint Status, uint Problem)
{
    internal bool IsPhysicalRadio
    {
        get
        {
            if (string.IsNullOrWhiteSpace(ParentId)) return false;
            if (Enumerator.Equals("USB", StringComparison.OrdinalIgnoreCase))
                return InstanceId.StartsWith(@"USB\VID_", StringComparison.OrdinalIgnoreCase)
                    && ParentId.StartsWith(@"USB\", StringComparison.OrdinalIgnoreCase)
                    && Service.Equals("BTHUSB", StringComparison.OrdinalIgnoreCase)
                    && HardwareIds.Any(id => id.StartsWith(@"USB\VID_", StringComparison.OrdinalIgnoreCase));

            // Non-USB radios using Microsoft's transport extension; reject software buses.
            return new[] { "ACPI", "PCI", "UART" }.Contains(Enumerator, StringComparer.OrdinalIgnoreCase)
                && InstanceId.StartsWith(Enumerator + @"\", StringComparison.OrdinalIgnoreCase)
                && Service.Equals("BTHMINI", StringComparison.OrdinalIgnoreCase)
                && CompatibleIds.Contains("MS_BTHX_BTHMINI", StringComparer.OrdinalIgnoreCase);
        }
    }

    internal BluetoothAdapterState DeviceState => StatusResult != 0 ? BluetoothAdapterState.Unknown
        : (Status & 0x400) != 0 && Problem == 22 ? BluetoothAdapterState.Disabled
        : (Status & 0x400) == 0 && (Status & 0x8) != 0 ? BluetoothAdapterState.Enabled
        : BluetoothAdapterState.Unknown;
}
