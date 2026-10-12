using BluetoothDisable.Core.Models;
using BluetoothDisable.Windows.Discovery;
using Xunit.Abstractions;

namespace BluetoothDisable.Tests;

public sealed class WindowsDiscoveryTests(ITestOutputHelper output)
{
    private static PnpRadioSnapshot Usb() => new(@"USB\VID_8087&PID_0026\1", "Radio", "BTHUSB", "USB",
        [@"USB\VID_8087&PID_0026"], [], @"USB\ROOT_HUB30\1", 0, 8, 0);

    [Fact]
    public void RecognizesUsbRadioWithoutGuessingFromItsName() => Assert.True(Usb().IsPhysicalRadio);

    [Theory]
    [InlineData("BTHENUM")]
    [InlineData("BTHLEDEVICE")]
    [InlineData("ROOT")]
    [InlineData("SWD")]
    public void RejectsPeripheralAndSoftwareEnumerators(string bus) =>
        Assert.False((Usb() with { Enumerator = bus, InstanceId = bus + @"\Bluetooth" }).IsPhysicalRadio);

    [Fact]
    public void RejectsUsbHubEvenWithBluetoothInItsName() =>
        Assert.False((Usb() with { Name = "Bluetooth hub", Service = "USBHUB3" }).IsPhysicalRadio);

    [Fact]
    public void RequiresParentEvidence() => Assert.False((Usb() with { ParentId = null }).IsPhysicalRadio);

    [Fact]
    public void RecognizesMicrosoftNonUsbTransport() => Assert.True((Usb() with
    {
        Enumerator = "ACPI", InstanceId = @"ACPI\RADIO\1", Service = "BTHMINI",
        ParentId = @"ACPI\BUS\1", CompatibleIds = ["MS_BTHX_BTHMINI"]
    }).IsPhysicalRadio);

    [Theory]
    [InlineData(0, 8, 0, BluetoothAdapterState.Enabled)]
    [InlineData(0, 0x400, 22, BluetoothAdapterState.Disabled)]
    [InlineData(0, 0x408, 10, BluetoothAdapterState.Unknown)]
    [InlineData(0, 0, 0, BluetoothAdapterState.Unknown)]
    [InlineData(5, 8, 0, BluetoothAdapterState.Unknown)]
    public void MapsOnlyConfirmedDeviceStates(uint result, uint flags, uint problem, BluetoothAdapterState expected) =>
        Assert.Equal(expected, (Usb() with { StatusResult = result, Status = flags, Problem = problem }).DeviceState);

    [Fact]
    [Trait("Category", "Hardware")]
    public async Task ReadOnlyDiscoveryOnThisWindowsHost()
    {
        var adapters = await new WindowsBluetoothAdapterDiscovery().GetAdaptersAsync();
        output.WriteLine(System.Text.Json.JsonSerializer.Serialize(adapters));
        Assert.All(adapters, adapter =>
        {
            Assert.True(adapter.IsPresent);
            Assert.Equal(BluetoothAdapterKind.PhysicalRadio, adapter.Kind);
            Assert.False(adapter.DeviceInstanceId.StartsWith("BTH", StringComparison.OrdinalIgnoreCase));
        });
        // Zero radios is valid on a CI VM; that does not validate physical hardware scenarios.
    }
}
