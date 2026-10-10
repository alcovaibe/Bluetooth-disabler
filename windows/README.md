# Bluetooth Disable for Windows

Самостоятельное приложение WinUI 3 для Windows 10 2004 (build 19041) и Windows 11.
Рабочая архитектура — x64. Конфигурации x86 и ARM64 исходного шаблона сохранены;
проверка ARM64 на устройстве относится к будущему этапу. Cover Mode отсутствует.

## Состав решения

```text
windows/
├── BluetoothDisable.sln
├── global.json
├── README.md
├── BluetoothDisable.App/
│   ├── BluetoothDisable.App.csproj
│   ├── App.xaml / App.xaml.cs
│   ├── MainWindow.xaml / MainWindow.xaml.cs
│   ├── Package.appxmanifest / app.manifest
│   ├── Assets/
│   └── Properties/
├── BluetoothDisable.Core/
│   ├── BluetoothDisable.Core.csproj
│   ├── Models/
│   │   ├── BluetoothAdapter.cs
│   │   ├── AdapterOperationResult.cs
│   │   └── DisablePersistence.cs
│   ├── Interfaces/
│   │   ├── IBluetoothAdapterDiscovery.cs
│   │   └── IBluetoothAdapterController.cs
│   └── Services/BluetoothAdapterService.cs
├── BluetoothDisable.Tests/
│   ├── BluetoothDisable.Tests.csproj
│   ├── BluetoothAdapterServiceTests.cs
│   └── Fakes/FakeBluetoothAdapterDiscovery.cs
└── installer/.gitkeep
```

Зависимости: **App → Core**, **Tests → Core**. Core использует только .NET и не
ссылается на WinUI, App или нативные библиотеки Windows. Тесты не требуют
Bluetooth-оборудования, административных прав или запуска приложения.

Исходные XAML, ресурсы, точка входа и профили упаковки сохранены. Окно пока пустое:
это подготовка архитектуры, без имитации работающей блокировки. ViewModels и
команды появятся вместе с реальным сценарием интерфейса.

## Версии и инструменты

| Компонент | Версия / настройка |
| --- | --- |
| .NET | 10 LTS |
| .NET SDK | 10.0.400, последующие исправления 10.0.4xx разрешены |
| App TFM | `net10.0-windows10.0.19041.0` |
| Core / Tests TFM | `net10.0` |
| Windows App SDK | `Microsoft.WindowsAppSDK` 2.5.1 |
| Windows SDK BuildTools (NuGet) | 10.0.28000.2705 |
| Windows SDK .NET projection | 10.0.19041.87 (`WindowsSdkPackageVersion`) |
| xUnit | 2.9.3 |
| Test SDK / VS runner | 17.14.1 / 3.1.4 |
| Coverlet collector | 6.0.4 |

[global.json](./global.json) действует только при работе из этого каталога и
его подкаталогов. SDK 9 не выбирается автоматически. Версии NuGet в исходном
WinUI-проекте уже совместимы со сборкой на .NET 10 и сохранены. Дополнительно
закреплена .NET-проекция Windows SDK 10.0.19041.87: версия 10.0.19041.57,
выбранная SDK по умолчанию, выдавала предупреждения `IL2081` / `IL2104`
при trimming в Release. Новая проекция устраняет эти предупреждения без
отключения trimming или анализаторов, сохраняя целевой build 19041.

Для работы из VS Code используйте установленный .NET SDK 10.0.400 и команды
ниже. На машине проверки также установлены Visual Studio 2022 17.14.41 и
Windows SDK 10.0.22621.0 / 10.0.26100.0; нативные инструменты Windows SDK и
MSIX/XAML должны быть доступны на Windows. NuGet BuildTools и Windows App SDK
восстанавливаются автоматически. MAUI для этого решения не нужен.

Для открытия и сборки `net10.0` непосредственно в Visual Studio требуется
**Visual Studio 2026 18.0+**, предпочтительно 18.9 для SDK 10.0.4xx, с
инструментами разработки WinUI/Windows App SDK и MSIX. Старый MSBuild из
Visual Studio 2022 не следует использовать вместо `dotnet build`:
[таблица совместимости Microsoft](https://learn.microsoft.com/en-us/dotnet/core/porting/versioning-sdk-msbuild-vs).

## Восстановление, сборка и тесты

В PowerShell перейдите в каталог, содержащий этот README, и выполните:

```powershell
dotnet restore BluetoothDisable.sln -p:Platform=x64
dotnet build BluetoothDisable.Core/BluetoothDisable.Core.csproj --no-restore
dotnet build BluetoothDisable.Tests/BluetoothDisable.Tests.csproj --no-restore
dotnet test BluetoothDisable.Tests/BluetoothDisable.Tests.csproj --no-build --no-restore --logger "trx;LogFileName=core-tests.trx" --results-directory TestResults
dotnet build BluetoothDisable.App/BluetoothDisable.App.csproj -p:Platform=x64 --no-restore
dotnet build BluetoothDisable.sln -p:Platform=x64 --no-restore
dotnet build BluetoothDisable.sln -c Release -p:Platform=x64
```

В конфигурации решения `Any CPU` App отображается на `x64`, а Core и Tests
остаются `Any CPU`. При сборке отдельного WinUI-проекта указывайте `Platform=x64`.
Release сохраняет настройки `PublishReadyToRun` и `PublishTrimmed` шаблона.

## Результаты проверки 09.10.2026

| Проверка | Фактический результат |
| --- | --- |
| Состав решения | App, Core, Tests; циклических ссылок нет |
| NuGet restore всего решения | Успешно |
| Отдельная сборка Core и Tests, Debug | Успешно, 0 предупреждений / 0 ошибок |
| xUnit | 11 пройдено, 0 ошибок, 0 пропущено |
| Отдельная сборка App, Debug x64 | Успешно, 0 предупреждений / 0 ошибок |
| Всё решение, Debug x64 и конфигурация по умолчанию | Успешно, 0 предупреждений / 0 ошибок |
| Всё решение, Release x64 с подробным анализом trimming | Успешно, 0 предупреждений / 0 ошибок |
| Git diff --check | Успешно |
| Подпись / установка MSIX / запуск окна | Не выполнялись |
| Запуск на Windows 10 / ARM64 / x86 | Не проверялся |

Команды выполнялись на Windows x64 через .NET SDK 10.0.400 (MSBuild
18.9.6). Для выполненных сборок дополнительная установка инструментов не
потребовалась. При первом восстановлении возникла временная блокировка файлов
HTTP-кеша NuGet другим процессом; автоматические повторные попытки завершились
успешно. Предупреждения об обрезке старой WinRT-проекции исправлены обновлением
пакета, без подавления предупреждений. Для подробной проверки использовался
дополнительный параметр `-p:TrimmerSingleWarn=false`.

Тесты проверяют несколько физических адаптеров, USB и отключённое состояние,
отсев неизвестных/виртуальных/периферийных и отсутствующих устройств, повторные
ID, неоднозначную классификацию, пустой результат, отмену и ошибки обнаружения.
TRX сохраняется в локальном [TestResults](./TestResults/), исключённом из Git.

## Упаковка и запуск

Сохранён single-project MSIX (`EnableMsixTooling=true`) с исходными Identity,
Publisher, ресурсами и `runFullTrust`. Минимальная версия ОС одновременно
выставлена в проекте и манифесте в 10.0.19041.0. `runFullTrust` не означает
автоматического получения административных прав.

Для запуска упакованного приложения выберите профиль
`BluetoothDisable.App (Package)` в совместимой Visual Studio, включите режим
разработчика Windows для локального развёртывания и настройте локальный
сертификат подписи, если этого требует развёртывание. Subject сертификата
должен соответствовать Publisher манифеста. Приватный ключ в Git не добавляется.
Простой запуск exe из каталога сборки не заменяет регистрацию MSIX-пакета.

Сборка, выпуск подписанного установщика, установка пакета и проверка окна —
разные проверки. Настройка сертификата и развёртывание не выполняются автоматически.

## Границы архитектуры

- [BluetoothAdapter](./BluetoothDisable.Core/Models/BluetoothAdapter.cs) хранит
  Windows device instance ID, имя, классификацию, состояние и присутствие.
  По умолчанию устройство неизвестно и не считается присутствующим.
- [IBluetoothAdapterDiscovery](./BluetoothDisable.Core/Interfaces/IBluetoothAdapterDiscovery.cs)
  — будущая реализация обнаружения, включая отключённые физические радиомодули.
- [BluetoothAdapterService](./BluetoothDisable.Core/Services/BluetoothAdapterService.cs)
  отбирает присутствующие физические адаптеры, сохраняет отключённые кандидаты
  для восстановления и устраняет повторы ID без учёта регистра. Неоднозначная
  классификация одного ID исключает его целиком. Ошибки обнаружения передаются
  вызывающему коду, а не превращаются в «адаптеров нет».
- [IBluetoothAdapterController](./BluetoothDisable.Core/Interfaces/IBluetoothAdapterController.cs)
  описывает отключение с явно выбранной длительностью и обратное включение.
  Реализации пока нет; сервис отбора этот интерфейс не вызывает.
- [AdapterOperationResult](./BluetoothDisable.Core/Models/AdapterOperationResult.cs)
  возвращает результат для отдельного адаптера, код `CONFIGRET`, необходимость
  перезагрузки и ошибку. Это позволяет отражать частичный успех для нескольких
  устройств. Отмена передаётся через `CancellationToken` и не равна откату.

P/Invoke, Windows-службы, повышение привилегий и фактическое отключение устройств
на этом этапе отсутствуют.

## Следующий этап: системная блокировка

1. Реализовать Windows-обнаружение через официальные API и проверку типа,
   свойств и топологии devnode. Класс Bluetooth или имя с текстом Bluetooth
   сами по себе недостаточны: периферия, виртуальные узлы, родительские USB-хабы
   и составные устройства не должны попадать в цели отключения. Поддержать
   несколько встроенных/USB-радиомодулей и обнаружение уже отключённых устройств.
2. Выделить минимальный компонент с административными правами и согласовать
   его запуск через UAC с моделью MSIX. Повторно разрешать instance ID и
   независимо проверять устройство на привилегированной стороне перед каждой
   операцией. Не доверять классификации, пришедшей из UI. Windows-служба не
   предусматривается без отдельного задания.
3. Реализовать вызовы
   [CM_Disable_DevNode](https://learn.microsoft.com/en-us/windows/win32/api/cfgmgr32/nf-cfgmgr32-cm_disable_devnode)
   с `CM_DISABLE_PERSIST` для `AcrossRestarts` и
   [CM_Enable_DevNode](https://learn.microsoft.com/en-us/windows/win32/api/cfgmgr32/nf-cfgmgr32-cm_enable_devnode)
   для восстановления. Проверять фактическое состояние после вызова и сохранять
   исходный `CONFIGRET`, обрабатывать отказ UAC, доступ, исчезновение устройства
   и необходимость перезагрузки.
4. Добавить журнал восстановления: ID и исходное состояние, факт изменения
   приложением и результат проверки. Не включать при разблокировке устройства,
   которые были отключены до работы приложения. Сохранять данные до изменения,
   восстанавливать незавершённую операцию после сбоя и отдельно учитывать
   частичный успех и отмену между адаптерами.
5. Различать сохраняемое отключение известных devnode и политику для новых USB
   устройств. `CM_DISABLE_PERSIST` сохраняет отключение после перезагрузки,
   но не реализует обнаружение новых адаптеров и не запрещает администратору
   включить оборудование другим способом. Обработку подключения и смены ID
   спроектировать отдельно, без обещания абсолютной защиты.
6. Добавить WinUI ViewModel, команды блокировки/разблокировки, отображение
   отдельных результатов и подтверждённого состояния. После реализации проверить
   MSIX/UAC, восстановление после перезагрузки/сбоя, Windows 10/11 и ARM64 на
   специально выделенном оборудовании.

## Git

Корневой [.gitignore](../.gitignore) сохраняет правила Android и исключает
локальные IDE-настройки, сборки, NuGet-пакеты, результаты тестирования и приватные
ключи подписи. Проекты, решение, XAML, манифесты, ресурсы и профили сборки остаются
доступны для добавления в Git. В installer сохранена заглушка до начала разработки
установщика.
