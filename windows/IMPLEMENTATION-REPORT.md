# Windows: интерфейс, обнаружение и установщик — 10.10.2026

Реализован интерфейс WinUI 3 и только чтение локальных Bluetooth-адаптеров. Управление оборудованием недоступно. Обновление подготовлено отдельным PR после слияния первоначальной настройки в PR #69.

## Проверено

- Debug и Release x64: восстановление и сборка всего решения, 0 ошибок и предупреждений.
- По 43 модульных теста в Debug и Release; отдельно один Hardware-тест на реальном Windows-компьютере.
- Intel(R) Wireless Bluetooth(R): найден именно адаптер, устройство работает, радиомодуль включён. Периферия и служебные узлы исключены. Процесс не повышен.
- Реальное окно: обновление, выбор, Tab/F5, недоступные команды, размеры 560×650 и 900×850, нормальное закрытие. Снимок и JSON: [UI evidence](./TestResults/ui/).
- EXE: настоящая установка для текущего пользователя, ярлык и запись удаления, повторная установка той же версии, запуск и стандартное удаление. [Логи](./TestResults/installer/).
- Пользователь отдельно подтвердил успешную установку EXE и запуск приложения на Windows 11 Pro. Это подтверждение не распространяется на перечисленные ниже непроверенные конфигурации.
- Строгий NuGet Audit: 31 пакет, 0 проблем; 16 custom/legacy лицензий остаются предметом обычной проверки перед публичным выпуском. [Отчёт](./TestResults/final-audit/nuget-audit.md).
- 22 существующих теста платформенного CI прошли. Для Windows использован временный каталог с полным путём, чтобы избежать различия long path / 8.3 alias в существующем тесте лицензий; сами тесты не менялись.
- Windows workflow дополнен сборкой EXE/MSIX, проверкой установки и публикацией диагностик. CI Gate и Android workflows не изменены. Результаты GitHub Actions публикуются в проверках нового PR отдельно от локальных результатов выше.

## Запуск и пакет

Инструкции, включая локальную подпись MSIX: [installer/README.md](./installer/README.md).
Основной установщик после локальной сборки: `BluetoothDisable-1.0.1-win-x64-setup.exe`.
Точные пути EXE, self-contained payload и MSIX: [build-info.json](./artifacts/installer/build-info.json).

## Оставшиеся ограничения

- Тестовый EXE и MSIX без публичной подписи; MSIX собран, но не установлен. Сертификаты и доверие Windows не менялись.
- Не проверены чистая Windows без средств разработки, Windows 10 build 19041, ARM64, разные DPI/мониторы, несколько реальных адаптеров, внешний USB и извлечение во время запроса.
- Отключённые устройства и ошибки классификации проверены модульными тестами. Реальное оборудование не отключалось; этот сценарий нужно проверить на выделенном компьютере с заранее отключённым адаптером.
- OEM-стеки с нестандартным setup class/службой могут не отображаться. Неизвестное состояние радио честно отображается как «Неизвестно». [Подробности](./BluetoothDisable.Windows/README.md).
- Постоянная идентичность MSIX и подпись публичного выпуска требуют решения владельца. Обновление между разными выпущенными версиями ещё не проверено.

## Изменённые файлы

- [.github/workflows/windows.yml](../.github/workflows/windows.yml)
- [windows/BluetoothDisable.App/BluetoothDisable.App.csproj](./BluetoothDisable.App/BluetoothDisable.App.csproj)
- [windows/BluetoothDisable.App/MainWindow.xaml](./BluetoothDisable.App/MainWindow.xaml)
- [windows/BluetoothDisable.App/MainWindow.xaml.cs](./BluetoothDisable.App/MainWindow.xaml.cs)
- [windows/BluetoothDisable.App/Package.appxmanifest](./BluetoothDisable.App/Package.appxmanifest)
- [windows/BluetoothDisable.Core/Models/BluetoothAdapter.cs](./BluetoothDisable.Core/Models/BluetoothAdapter.cs)
- [windows/BluetoothDisable.Core/Services/BluetoothAdapterService.cs](./BluetoothDisable.Core/Services/BluetoothAdapterService.cs)
- [windows/BluetoothDisable.Tests/BluetoothDisable.Tests.csproj](./BluetoothDisable.Tests/BluetoothDisable.Tests.csproj)
- [windows/BluetoothDisable.sln](./BluetoothDisable.sln)
- [windows/README.md](./README.md)

## Созданные файлы

- [windows/.gitignore](./.gitignore)
- [windows/BluetoothDisable.App/Assets/AppIcon.ico](./BluetoothDisable.App/Assets/AppIcon.ico)
- [windows/BluetoothDisable.App/Assets/BluetoothIcon.png](./BluetoothDisable.App/Assets/BluetoothIcon.png)
- [windows/BluetoothDisable.App/Assets/BluetoothSplash.scale-200.png](./BluetoothDisable.App/Assets/BluetoothSplash.scale-200.png)
- [windows/BluetoothDisable.App/Assets/BluetoothSquare150.scale-200.png](./BluetoothDisable.App/Assets/BluetoothSquare150.scale-200.png)
- [windows/BluetoothDisable.App/Assets/BluetoothSquare44.scale-200.png](./BluetoothDisable.App/Assets/BluetoothSquare44.scale-200.png)
- [windows/BluetoothDisable.App/Assets/BluetoothSquare44.targetsize-48.png](./BluetoothDisable.App/Assets/BluetoothSquare44.targetsize-48.png)
- [windows/BluetoothDisable.App/Assets/BluetoothStore.scale-200.png](./BluetoothDisable.App/Assets/BluetoothStore.scale-200.png)
- [windows/BluetoothDisable.App/Assets/BluetoothWide.scale-200.png](./BluetoothDisable.App/Assets/BluetoothWide.scale-200.png)
- [windows/BluetoothDisable.App/ViewModels/AdapterItem.cs](./BluetoothDisable.App/ViewModels/AdapterItem.cs)
- [windows/BluetoothDisable.App/ViewModels/MainViewModel.cs](./BluetoothDisable.App/ViewModels/MainViewModel.cs)
- [windows/BluetoothDisable.Tests/MainViewModelTests.cs](./BluetoothDisable.Tests/MainViewModelTests.cs)
- [windows/BluetoothDisable.Tests/WindowsDiscoveryTests.cs](./BluetoothDisable.Tests/WindowsDiscoveryTests.cs)
- [windows/BluetoothDisable.Windows/BluetoothDisable.Windows.csproj](./BluetoothDisable.Windows/BluetoothDisable.Windows.csproj)
- [windows/BluetoothDisable.Windows/Discovery/PnpRadioSnapshot.cs](./BluetoothDisable.Windows/Discovery/PnpRadioSnapshot.cs)
- [windows/BluetoothDisable.Windows/Discovery/SetupApi.cs](./BluetoothDisable.Windows/Discovery/SetupApi.cs)
- [windows/BluetoothDisable.Windows/Discovery/WindowsBluetoothAdapterDiscovery.cs](./BluetoothDisable.Windows/Discovery/WindowsBluetoothAdapterDiscovery.cs)
- [windows/BluetoothDisable.Windows/README.md](./BluetoothDisable.Windows/README.md)
- [windows/installer/BluetoothDisable.iss](./installer/BluetoothDisable.iss)
- [windows/installer/Build-Installer.ps1](./installer/Build-Installer.ps1)
- [windows/installer/Get-InnoSetup.ps1](./installer/Get-InnoSetup.ps1)
- [windows/installer/New-AppAssets.ps1](./installer/New-AppAssets.ps1)
- [windows/installer/README.md](./installer/README.md)
- [windows/installer/THIRD-PARTY-NOTICES.md](./installer/THIRD-PARTY-NOTICES.md)
- [windows/installer/Test-App.ps1](./installer/Test-App.ps1)
- [windows/installer/Test-Installer.ps1](./installer/Test-Installer.ps1)
- [windows/IMPLEMENTATION-REPORT.md](./IMPLEMENTATION-REPORT.md)
