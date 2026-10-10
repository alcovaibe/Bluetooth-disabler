using BluetoothDisable.Core.Models;
using BluetoothDisable.Core.Services;
using BluetoothDisable.Tests.Fakes;

namespace BluetoothDisable.Tests;

public sealed class BluetoothAdapterServiceTests
{
    [Fact]
    public async Task KeepsMultiplePhysicalRadiosIncludingUsbAndDisabledRadios()
    {
        BluetoothAdapter[] radios =
        [
            new(@"PCI\RADIO\1", "Internal radio", BluetoothAdapterKind.PhysicalRadio,
                BluetoothAdapterState.Enabled, isPresent: true),
            new(@"USB\VID_1234&PID_5678\2", "USB radio", BluetoothAdapterKind.PhysicalRadio,
                BluetoothAdapterState.Disabled, isPresent: true)
        ];
        var service = new BluetoothAdapterService(new FakeBluetoothAdapterDiscovery { Adapters = radios });

        var result = await service.GetPhysicalAdaptersAsync();

        Assert.Equal(radios, result);
    }

    [Theory]
    [InlineData(BluetoothAdapterKind.Unknown)]
    [InlineData(BluetoothAdapterKind.VirtualDevice)]
    [InlineData(BluetoothAdapterKind.Peripheral)]
    public async Task RejectsUnverifiedDevicesEvenWhenTheirNameSaysBluetooth(BluetoothAdapterKind kind)
    {
        var discovery = new FakeBluetoothAdapterDiscovery
        {
            Adapters = [new("DEVICE\\1", "Bluetooth adapter", kind, isPresent: true)]
        };

        var result = await new BluetoothAdapterService(discovery)
            .GetPhysicalAdaptersAsync();

        Assert.Empty(result);
    }

    [Fact]
    public async Task ExcludesDisconnectedRadios()
    {
        var discovery = new FakeBluetoothAdapterDiscovery
        {
            Adapters = [new("USB\\1", "Removed radio", BluetoothAdapterKind.PhysicalRadio)]
        };

        var result = await new BluetoothAdapterService(discovery)
            .GetPhysicalAdaptersAsync();

        Assert.Empty(result);
    }

    [Fact]
    public async Task DeduplicatesDeviceInstanceIdsIgnoringCase()
    {
        var discovery = new FakeBluetoothAdapterDiscovery
        {
            Adapters =
            [
                new("USB\\RADIO", "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true),
                new("usb\\radio", "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true)
            ]
        };

        var result = await new BluetoothAdapterService(discovery)
            .GetPhysicalAdaptersAsync();

        Assert.Equal("USB\\RADIO", Assert.Single(result).DeviceInstanceId);
    }

    [Fact]
    public async Task RejectsIdentityWithConflictingClassification()
    {
        var discovery = new FakeBluetoothAdapterDiscovery
        {
            Adapters =
            [
                new("USB\\RADIO", "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true),
                new("usb\\radio", "Unknown device", isPresent: true)
            ]
        };

        var result = await new BluetoothAdapterService(discovery)
            .GetPhysicalAdaptersAsync();

        Assert.Empty(result);
    }

    [Fact]
    public async Task ReturnsEmptyWhenNoAdaptersAreFound()
    {
        var service = new BluetoothAdapterService(new FakeBluetoothAdapterDiscovery());

        Assert.Empty(await service.GetPhysicalAdaptersAsync());
    }

    [Fact]
    public async Task PassesCancellationToDiscovery()
    {
        using var cancellation = new CancellationTokenSource();
        var discovery = new FakeBluetoothAdapterDiscovery();
        var service = new BluetoothAdapterService(discovery);

        await service.GetPhysicalAdaptersAsync(cancellation.Token);

        Assert.Equal(cancellation.Token, discovery.LastCancellationToken);
    }

    [Fact]
    public async Task CancelledRequestDoesNotStartDiscovery()
    {
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        var discovery = new FakeBluetoothAdapterDiscovery();
        var service = new BluetoothAdapterService(discovery);

        await Assert.ThrowsAsync<OperationCanceledException>(
            () => service.GetPhysicalAdaptersAsync(cancellation.Token));
        Assert.Equal(0, discovery.CallCount);
    }

    [Fact]
    public async Task DoesNotHideDiscoveryFailureAsNoAdaptersFound()
    {
        var failure = new InvalidOperationException("Device enumeration failed.");
        var service = new BluetoothAdapterService(new FakeBluetoothAdapterDiscovery { Failure = failure });

        var actual = await Assert.ThrowsAsync<InvalidOperationException>(
            () => service.GetPhysicalAdaptersAsync());

        Assert.Same(failure, actual);
    }
}
