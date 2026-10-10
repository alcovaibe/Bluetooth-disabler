using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using BluetoothDisable.Core.Services;

namespace BluetoothDisable.App.ViewModels;

public sealed class MainViewModel(BluetoothAdapterService service) : INotifyPropertyChanged, IDisposable
{
    private readonly CancellationTokenSource _lifetime = new();
    private bool _disposed;
    private bool _isLoading;
    private bool _hasLoaded;
    private string _error = "";
    private IReadOnlyList<AdapterItem> _adapters = Array.Empty<AdapterItem>();
    private AdapterItem? _selectedAdapter;

    public event PropertyChangedEventHandler? PropertyChanged;
    public IReadOnlyList<AdapterItem> Adapters => _adapters;
    public bool IsLoading => _isLoading;
    public bool CanRefresh => !_isLoading && !_disposed;
    public bool ShowEmpty => _hasLoaded && !_isLoading && !HasError && _adapters.Count == 0;
    public bool HasError => _error.Length > 0;
    public string ErrorMessage => _error;
    public string StatusText => _isLoading ? "Поиск локальных адаптеров…"
        : HasError ? "Не удалось обновить список"
        : !_hasLoaded ? "Готово к поиску" : $"Найдено адаптеров: {_adapters.Count}";
    public string SelectionText => SelectedAdapter is null ? "Выберите адаптер из списка" : $"Выбран: {SelectedAdapter.Name}";
    public AdapterItem? SelectedAdapter
    {
        get => _selectedAdapter;
        set
        {
            if (ReferenceEquals(_selectedAdapter, value)) return;
            _selectedAdapter = value;
            Notify();
            Notify(nameof(SelectionText));
        }
    }

    // All entry points run on the UI context; only one refresh can be in flight.
    public async Task RefreshAsync()
    {
        if (!CanRefresh) return;
        var selectedId = SelectedAdapter?.InstanceId;
        _isLoading = true;
        _error = "";
        UpdateState();
        try
        {
            var found = await service.GetPhysicalAdaptersAsync(_lifetime.Token);
            if (_disposed) return;
            _adapters = found.OrderBy(item => item.DisplayName, StringComparer.CurrentCultureIgnoreCase)
                .Select(item => new AdapterItem(item)).ToArray();
            Notify(nameof(Adapters));
            SelectedAdapter = _adapters.FirstOrDefault(item => string.Equals(item.InstanceId, selectedId, StringComparison.OrdinalIgnoreCase));
        }
        catch (OperationCanceledException) when (_lifetime.IsCancellationRequested) { }
        catch (Exception ex)
        {
            if (_disposed) return;
            _adapters = Array.Empty<AdapterItem>();
            SelectedAdapter = null;
            Notify(nameof(Adapters));
            _error = $"Не удалось прочитать список устройств Windows. Попробуйте обновить список. {ex.Message}";
        }
        finally
        {
            _isLoading = false;
            _hasLoaded = true;
            if (!_disposed) UpdateState();
            else _lifetime.Dispose();
        }
    }

    private void UpdateState()
    {
        foreach (var name in new[] { nameof(IsLoading), nameof(CanRefresh), nameof(ShowEmpty), nameof(HasError), nameof(ErrorMessage), nameof(StatusText) })
            Notify(name);
    }
    private void Notify([CallerMemberName] string? name = null) => PropertyChanged?.Invoke(this, new(name));
    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _lifetime.Cancel();
        if (!_isLoading) _lifetime.Dispose();
    }
}
