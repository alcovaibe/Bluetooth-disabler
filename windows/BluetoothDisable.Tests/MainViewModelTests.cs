using BluetoothDisable.App.ViewModels;
using BluetoothDisable.Core.Interfaces;
using BluetoothDisable.Core.Models;
using BluetoothDisable.Core.Services;

namespace BluetoothDisable.Tests;

public sealed class MainViewModelTests
{
    private sealed class Discovery : IBluetoothAdapterDiscovery
    {
        public int Calls { get; private set; }
        public Func<CancellationToken, Task<IReadOnlyList<BluetoothAdapter>>> Read { get; set; } = _ => Task.FromResult<IReadOnlyList<BluetoothAdapter>>([]);
        public Task<IReadOnlyList<BluetoothAdapter>> GetAdaptersAsync(CancellationToken cancellationToken = default)
        { Calls++; return Read(cancellationToken); }
    }
    private static BluetoothAdapter Radio(string id = "USB\\RADIO") => new(id, "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true);

    [Fact]
    public async Task EmptyAndErrorAreDifferentAndRetryRecovers()
    {
        var discovery = new Discovery();
        using var vm = new MainViewModel(new(discovery));
        await vm.RefreshAsync();
        Assert.True(vm.ShowEmpty);
        discovery.Read = _ => throw new InvalidOperationException("API failure");
        await vm.RefreshAsync();
        Assert.True(vm.HasError);
        Assert.False(vm.ShowEmpty);
        Assert.True(vm.CanRefresh);
        discovery.Read = _ => Task.FromResult<IReadOnlyList<BluetoothAdapter>>([Radio()]);
        await vm.RefreshAsync();
        Assert.False(vm.HasError);
        Assert.Single(vm.Adapters);
    }

    [Fact]
    public async Task OverlappingRefreshDoesNotStartAnotherEnumeration()
    {
        var pending = new TaskCompletionSource<IReadOnlyList<BluetoothAdapter>>();
        var discovery = new Discovery { Read = _ => pending.Task };
        using var vm = new MainViewModel(new(discovery));
        var first = vm.RefreshAsync();
        Assert.True(vm.IsLoading);
        Assert.Equal("0", vm.RefreshRevision);
        await vm.RefreshAsync();
        Assert.Equal(1, discovery.Calls);
        Assert.Equal("0", vm.RefreshRevision);
        pending.SetResult([Radio()]);
        await first;
        Assert.Single(vm.Adapters);
        Assert.False(vm.IsLoading);
        Assert.Equal("1", vm.RefreshRevision);
    }

    [Fact]
    public async Task SelectionSurvivesRefreshByIdAndDuplicatesAreRemoved()
    {
        var discovery = new Discovery { Read = _ => Task.FromResult<IReadOnlyList<BluetoothAdapter>>([Radio(), Radio("usb\\radio")]) };
        using var vm = new MainViewModel(new(discovery));
        await vm.RefreshAsync();
        vm.SelectedAdapter = Assert.Single(vm.Adapters);
        await vm.RefreshAsync();
        Assert.Same(Assert.Single(vm.Adapters), vm.SelectedAdapter);
    }

    [Fact]
    public async Task ClosingWindowCancelsDiscoveryAndDoesNotPublishLateResults()
    {
        var pending = new TaskCompletionSource<IReadOnlyList<BluetoothAdapter>>();
        CancellationToken observed = default;
        var discovery = new Discovery { Read = token => { observed = token; return pending.Task; } };
        var vm = new MainViewModel(new(discovery));
        var refresh = vm.RefreshAsync();
        vm.Dispose();
        Assert.True(observed.IsCancellationRequested);
        pending.SetResult([Radio()]);
        await refresh;
        Assert.Empty(vm.Adapters);
        Assert.False(vm.CanRefresh);
        Assert.Equal("0", vm.RefreshRevision);
    }

    [Fact]
    public async Task CompletedRefreshRevisionAdvancesOnSuccessAndFailure()
    {
        var discovery = new Discovery();
        using var vm = new MainViewModel(new(discovery));
        await vm.RefreshAsync();
        Assert.Equal("1", vm.RefreshRevision);
        discovery.Read = _ => throw new InvalidOperationException("API failure");
        await vm.RefreshAsync();
        Assert.Equal("2", vm.RefreshRevision);
        Assert.True(vm.HasError);
    }

    [Fact]
    public void UnknownRadioIsNotPresentedAsOff()
    {
        var item = new AdapterItem(Radio());
        Assert.Contains("Неизвестно", item.RadioStateText);
        Assert.Contains("Неизвестно", item.DeviceStateText);
    }

    [Fact]
    public async Task SameSelectionDoesNotReenterTwoWayBindings()
    {
        var discovery = new Discovery { Read = _ => Task.FromResult<IReadOnlyList<BluetoothAdapter>>([Radio()]) };
        using var vm = new MainViewModel(new(discovery));
        await vm.RefreshAsync();
        var changes = 0;
        vm.PropertyChanged += (_, e) => { if (e.PropertyName == nameof(vm.SelectedAdapter)) changes++; };
        vm.SelectedAdapter = vm.Adapters[0];
        vm.SelectedAdapter = vm.Adapters[0];
        Assert.Equal(1, changes);
    }

    [Theory]
    [InlineData(BluetoothRadioState.On, BluetoothRadioState.Off)]
    [InlineData(BluetoothRadioState.Off, BluetoothRadioState.On)]
    [InlineData(BluetoothRadioState.Unknown, BluetoothRadioState.On)]
    public async Task ConflictingRadioObservationsAreNotChosenArbitrarily(BluetoothRadioState first, BluetoothRadioState second)
    {
        var discovery = new Discovery { Read = _ => Task.FromResult<IReadOnlyList<BluetoothAdapter>>([
            new("USB\\1", "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true, radioState: first),
            new("usb\\1", "Radio", BluetoothAdapterKind.PhysicalRadio, isPresent: true, radioState: second)]) };
        Assert.Empty(await new BluetoothAdapterService(discovery).GetPhysicalAdaptersAsync());
    }
}
