# Обнаружение локальных Bluetooth-адаптеров

Платформенная библиотека зависит от Core и Windows SDK projections, но не от
WinUI. App использует её через IBluetoothAdapterDiscovery. Core остаётся net10.0
и не содержит P/Invoke. Tests имеет Windows TFM для проверки этой реализации;
модели представления проверяются без создания WinUI-окна.

## Источники данных

1. SetupDiGetClassDevsW / SetupDiEnumDeviceInfo с классом Bluetooth и
   DIGCF_PRESENT перечисляют присутствующие devnode, включая отключённые.
   Старые записи извлечённых устройств не показываются.
2. Читаются instance ID, friendly name (fallback: device description / ID),
   служба драйвера, enumerator, hardware/compatible IDs и родительский devnode.
3. USB-радио принимается только при Bluetooth setup class, USB VID instance/hardware ID,
   службе BTHUSB и USB-родителе. Программные ROOT/SWD, BTH/BTHENUM/BTHLEDEVICE,
   периферия и USB-хабы не принимаются. Для не-USB проверяются транспорт ACPI/PCI/UART,
   BTHMINI и идентификатор MS_BTHX_BTHMINI. Неизвестные OEM-стеки могут быть пропущены.
   Классификатор служит только для отображения, не является границей безопасности
   для будущего изменения оборудования.
4. CM_Get_DevNode_Status сообщает состояние устройства. Только DN_STARTED
   без DN_HAS_PROBLEM означает «работает»; DN_HAS_PROBLEM + CM_PROB_DISABLED (22)
   означает отключение в диспетчере устройств. Иные проблемы/ошибки означают
   «Неизвестно», с кодом проблемы или CONFIGRET.
5. WinRT BluetoothAdapter.GetDeviceSelector + DeviceInformation возвращают
   интерфейсы локальных радио. Они сопоставляются с PnP только по
   System.Devices.DeviceInstanceId. GetRadioAsync / Radio.State дают отдельное
   состояние радиомодуля. Сопоставления по имени или порядку нет.

Выключенный радиомодуль не означает отключённый devnode. Отсутствие WinRT
интерфейса, отказ доступа, таймаут или неизвестное состояние не трактуются
как Off. PnP-запись сохраняется; состояние радио показывается как «Неизвестно».
Данные являются снимком на момент обновления, разные API не дают атомарного
снимка. Состояние после внешних изменений перечитывается кнопкой или F5.

## Асинхронность и ошибки

SetupAPI работает в Task.Run, WinRT вызовы ожидаются асинхронно; на радио-часть
есть таймаут 10 секунд. В один момент UI допускает только одно обновление.
Список заменяется целиком после завершения; Core исключает дубликаты и
противоречивые состояния. Выбор сохраняется по instance ID. При общей ошибке
старый список очищается и выводится ошибка, а не «адаптеров нет».
Закрытие окна отменяет запрос и запрещает публикацию позднего результата.

Нет вызовов SetStateAsync, CM_Disable_DevNode, CM_Enable_DevNode,
RequestAccessAsync, установки драйверов, UAC, автозапуска или службы.
Разрешение bluetooth в MSIX нужно для чтения WinRT Bluetooth API.

## Проверки и ограничения

Из корня репозитория:

```powershell
dotnet test windows/BluetoothDisable.Tests/BluetoothDisable.Tests.csproj --filter 'Category!=Hardware'
dotnet test windows/BluetoothDisable.Tests/BluetoothDisable.Tests.csproj --filter 'Category=Hardware' --logger 'console;verbosity=detailed'
```

Вторая команда действительно читает устройства текущей Windows. На CI runner
нулевой результат допустим и не подтверждает работу с физическими адаптерами.
Локально проверен Intel(R) Wireless Bluetooth(R), USB VID_8087/PID_0026:
работающее устройство и включённое радио. Наушники, мышь и служебные узлы
в список приложения не попали. Проверка выполнена без административных прав.

Отключение оборудования для проверки не выполнялось. Нужны отдельные испытания
с заранее отключённым устройством, несколькими радиомодулями, внешним USB,
извлечением во время чтения, ошибками драйвера, Windows 10 и чистой Windows 11.
OEM Bluetooth-стеки с собственным setup class/службой и неподтверждённой
топологией могут не отображаться. ARM64 ещё не проверена.

## Документация Microsoft

- [SetupDiGetClassDevsW](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupdigetclassdevsw).
- [CM_Get_DevNode_Status](https://learn.microsoft.com/en-us/windows/win32/api/cfgmgr32/nf-cfgmgr32-cm_get_devnode_status).
- [Bluetooth host radio support](https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/bluetooth-host-radio-support).
- [GetRadioAsync](https://learn.microsoft.com/en-us/uwp/api/windows.devices.bluetooth.bluetoothadapter.getradioasync).
- [Radio.State](https://learn.microsoft.com/en-us/uwp/api/windows.devices.radios.radio.state).