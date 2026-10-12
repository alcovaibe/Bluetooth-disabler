using BluetoothDisable.Core.Models;
namespace BluetoothDisable.App.ViewModels;

public sealed class AdapterItem(BluetoothAdapter adapter)
{
    public string Name => adapter.DisplayName;
    public string InstanceId => adapter.DeviceInstanceId;
    public string PresenceText => adapter.IsPresent ? "Устройство присутствует" : "Устройство отсутствует";
    public string DeviceStateText => adapter.State switch
    {
        BluetoothAdapterState.Enabled => "Устройство: работает",
        BluetoothAdapterState.Disabled => "Устройство: отключено в диспетчере устройств",
        _ => "Устройство: Неизвестно"
    };
    public string RadioStateText => adapter.RadioState switch
    {
        BluetoothRadioState.On => "Радиомодуль: включён",
        BluetoothRadioState.Off => "Радиомодуль: выключен",
        BluetoothRadioState.Disabled => "Радиомодуль: недоступен",
        _ => "Радиомодуль: Неизвестно"
    };
    public string Details => string.Join(" · ", PresenceText, DeviceStateText, RadioStateText);
    public string Note => (adapter.DeviceProblemCode is uint code ? $"Код проблемы Windows: {code}. " : "")
        + (adapter.ObservationNote ?? "");
    public string AccessibleName => $"{Name}. {Details}. {InstanceId}. {Note}";
}