using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace BluetoothDisable.Windows.Discovery;

internal static class SetupApi
{
    private static readonly Guid BluetoothClass = new("e0cbf06c-cd8b-4647-bb8a-263b43f0f974");

    internal static IReadOnlyList<PnpRadioSnapshot> Read(CancellationToken token)
    {
        var classId = BluetoothClass;
        using var devices = SetupDiGetClassDevsW(ref classId, null, 0, 2); // DIGCF_PRESENT includes disabled devnodes.
        if (devices.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error(), "SetupDiGetClassDevsW failed.");
        var result = new List<PnpRadioSnapshot>();
        for (uint index = 0; ; index++)
        {
            token.ThrowIfCancellationRequested();
            var data = new DeviceInfo { Size = (uint)Marshal.SizeOf<DeviceInfo>() };
            if (!SetupDiEnumDeviceInfo(devices, index, ref data))
            {
                var error = Marshal.GetLastWin32Error();
                if (error == 259) break; // ERROR_NO_MORE_ITEMS only; other failures are not an empty list.
                throw new Win32Exception(error, "SetupDiEnumDeviceInfo failed.");
            }
            var id = DeviceId(data.DevInst);
            if (id is null) continue; // Removed during enumeration.
            string First(uint key) => Property(devices, ref data, key).FirstOrDefault() ?? "";
            var statusResult = CM_Get_DevNode_Status(out var status, out var problem, data.DevInst, 0);
            if (statusResult == 0x0D) continue; // CR_NO_SUCH_DEVNODE: the snapshot is no longer present.
            var name = First(12); // SPDRP_FRIENDLYNAME, then SPDRP_DEVICEDESC.
            if (string.IsNullOrWhiteSpace(name)) name = First(0);
            result.Add(new(id, string.IsNullOrWhiteSpace(name) ? id : name,
                First(4), First(22), Property(devices, ref data, 1), Property(devices, ref data, 2),
                ParentId(data.DevInst),
                statusResult, status, problem));
        }
        return result;
    }

    private static string? ParentId(uint instance)
    {
        var status = CM_Get_Parent(out var parent, instance, 0);
        if (status == 0x0D) return null;
        if (status != 0) throw new InvalidOperationException($"CM_Get_Parent: CONFIGRET=0x{status:X}.");
        return DeviceId(parent);
    }

    private static string? DeviceId(uint instance)
    {
        var status = CM_Get_Device_ID_Size(out var length, instance, 0);
        if (status == 0x0D) return null;
        if (status != 0) throw new InvalidOperationException($"CM_Get_Device_ID_Size: CONFIGRET=0x{status:X}.");
        var buffer = new StringBuilder(checked((int)length + 1));
        status = CM_Get_Device_IDW(instance, buffer, (uint)buffer.Capacity, 0);
        if (status == 0x0D) return null;
        if (status != 0) throw new InvalidOperationException($"CM_Get_Device_IDW: CONFIGRET=0x{status:X}.");
        return buffer.ToString();
    }

    private static string[] Property(DeviceInfoSet devices, ref DeviceInfo data, uint property)
    {
        byte[] buffer = new byte[512];
        for (var attempt = 0; attempt < 3; attempt++)
        {
            if (SetupDiGetDeviceRegistryPropertyW(devices, ref data, property, out var type,
                buffer, (uint)buffer.Length, out var required))
            {
                if (type is not (1 or 7)) return []; // REG_SZ / REG_MULTI_SZ only.
                return Encoding.Unicode.GetString(buffer, 0, checked((int)required))
                    .Split('\0', StringSplitOptions.RemoveEmptyEntries);
            }
            var error = Marshal.GetLastWin32Error();
            if (error is 13 or 2) return []; // An optional property is absent.
            if (error != 122 || required > 1024 * 1024)
                throw new Win32Exception(error, $"Reading PnP property {property} failed.");
            buffer = new byte[required];
        }
        throw new InvalidOperationException("PnP properties changed repeatedly during discovery. Refresh the list.");
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct DeviceInfo { public uint Size; public Guid ClassGuid; public uint DevInst; public nuint Reserved; }
    private sealed class DeviceInfoSet : SafeHandleZeroOrMinusOneIsInvalid
    {
        public DeviceInfoSet() : base(true) { }
        protected override bool ReleaseHandle() => SetupDiDestroyDeviceInfoList(handle);
    }

    [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
    private static extern DeviceInfoSet SetupDiGetClassDevsW(ref Guid classGuid, string? enumerator, nint parent, uint flags);
    [DllImport("setupapi.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetupDiEnumDeviceInfo(DeviceInfoSet set, uint index, ref DeviceInfo data);
    [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetupDiGetDeviceRegistryPropertyW(DeviceInfoSet set, ref DeviceInfo data,
        uint property, out uint type, [Out] byte[] buffer, uint size, out uint required);
    [DllImport("setupapi.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetupDiDestroyDeviceInfoList(nint set);
    [DllImport("cfgmgr32.dll")]
    private static extern uint CM_Get_DevNode_Status(out uint status, out uint problem, uint instance, uint flags);
    [DllImport("cfgmgr32.dll")]
    private static extern uint CM_Get_Parent(out uint parent, uint instance, uint flags);
    [DllImport("cfgmgr32.dll")]
    private static extern uint CM_Get_Device_ID_Size(out uint length, uint instance, uint flags);
    [DllImport("cfgmgr32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern uint CM_Get_Device_IDW(uint instance, StringBuilder buffer, uint length, uint flags);
}
