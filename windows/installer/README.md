# Установка и сборка Windows preview

Основной результат этого этапа — **EXE-установщик x64**. Он устанавливает
self-contained WinUI-приложение для текущего пользователя, добавляет ярлык
в меню «Пуск» и запись в «Установленные приложения». Для установки, запуска,
обнаружения и удаления не нужны права администратора. Автозапуск отсутствует.

MSIX-проект сохранён. Тот же сценарий отдельно собирает MSIX без подписи:
EXE устанавливает unpackaged-вариант приложения, он не импортирует сертификаты
и не запускает установку MSIX скрытно. Не устанавливайте оба варианта в один
профиль при обычном тестировании: это две независимые установки.

## Готовые файлы

После сборки:

- EXE: [BluetoothDisable-1.0.1-win-x64-setup.exe](../artifacts/installer/BluetoothDisable-1.0.1-win-x64-setup.exe).
- MSIX: [каталог пакетов](../artifacts/installer/msix/).
- Точные пути и версия: [build-info.json](../artifacts/installer/build-info.json).
- Папка unpackaged-приложения указана в поле `payload` этого JSON.

Оба артефакта являются **неподписанными тестовыми сборками**.
EXE не требует доверия тестовому сертификату; Windows может показать
«Неизвестный издатель» / SmartScreen. Корпоративная политика может запретить
такой запуск. Не отключайте защиту Windows ради установки.
Публичное распространение требует отдельного решения по подписи и издателю.

## Сборка

Из корня репозитория в PowerShell 7:

```powershell
./windows/installer/Build-Installer.ps1
```

Нужны .NET SDK из [global.json](../global.json), Windows SDK и инструменты
WinUI/MSIX, описанные в [Windows README](../README.md). Скрипт загружает
Inno Setup **6.7.3** из официального релиза, проверяет закреплённый SHA256
и Authenticode издателя Pyrsys B.V. и устанавливает компилятор для текущего
пользователя в игнорируемую папку `artifacts/tools/InnoSetup`.
Это также создаёт запись удаления самого Inno Setup; его можно удалить отдельно.
Существующий компилятор можно передать явно:

```powershell
./windows/installer/Build-Installer.ps1 -InnoCompiler 'C:/Program Files (x86)/Inno Setup 6/ISCC.exe'
```

Сборка изолирует промежуточные файлы packaged/unpackaged в отдельной папке
для каждого запуска. Не удаляет предыдущие исходники или пользовательские
установки. Итоговые артефакты одной версии обновляются.
Сценарий ищет установленный MSVC-инструмент mspdbcmf.exe через vswhere,
чтобы MSIX tooling мог создавать дополнительный пакет символов без предупреждений.
Компиляция, валидация пакета и анализаторы включены. Binlog и дополнительные
символы сохраняются в каталоге сборки; установщик не требует пакета символов.

В пакет попадает только результат публикации Windows-приложения, его ресурсы,
лицензия проекта и уведомления зависимостей. Исходники Android и репозиторий
целиком не копируются. .NET и Windows App SDK включены в пакет.
Проверка чистой Windows без установленного SDK всё равно необходима:
успешный запуск на машине разработчика не заменяет такую проверку.

## Установка EXE и запуск

Запустите созданный EXE обычным двойным щелчком, затем откройте
**Bluetooth Disable** через меню «Пуск». Каталог по умолчанию:

`%LOCALAPPDATA%/Programs/Bluetooth Disable`

Для удаления: «Параметры → Приложения → Установленные приложения →
Bluetooth Disable → Удалить». Повторный запуск установщика той же версии
поддерживается. Переход между разными выпущенными версиями пока не проверен.

Можно запустить без установки из папки публикации:

```powershell
$build = Get-Content ./windows/artifacts/installer/build-info.json -Raw | ConvertFrom-Json
& (Join-Path $build.payload 'BluetoothDisable.App.exe')
```

## Проверка установщика

```powershell
./windows/installer/Test-Installer.ps1
```

Сценарий выполняет настоящую установку в отдельный каталог внутри
`artifacts/install-smoke`, проверяет запись удаления и ярлык, повторно
устанавливает ту же версию, запускает окно и штатно удаляет приложение.
При существующей установке/ярлыке он останавливается, чтобы не затронуть
пользовательскую установку. Логи: [TestResults/installer](../TestResults/installer/).
Реестр HKCU и ярлык меняются только на время теста; состояние Bluetooth не меняется.

Проверка интерфейса на интерактивном рабочем столе:

```powershell
$build = Get-Content ./windows/artifacts/installer/build-info.json -Raw | ConvertFrom-Json
./windows/installer/Test-App.ps1 -Executable (Join-Path $build.payload 'BluetoothDisable.App.exe')
```

Проверяет обнаружение, обновление, выбор, недоступные команды, Tab/F5,
изменение размера и нормальное закрытие. Сохраняет снимок окна и JSON.
Во время теста не переключайте фокус на другие приложения.

## Отдельное локальное тестирование MSIX

EXE не требует следующих действий. Для установки MSIX сначала подпишите
его сертификатом, чей Subject совпадает с `Publisher=CN=Иван`.
Identity/Publisher сохранены от первоначального проекта и ещё не утверждены
для публичного выпуска.

Пример ручной локальной подписи (не выполняется скриптом сборки):

```powershell
$build = Get-Content ./windows/artifacts/installer/build-info.json -Raw | ConvertFrom-Json
$msix = $build.msix[0]
$cert = New-SelfSignedCertificate -Type Custom -Subject 'CN=Иван' -FriendlyName 'Bluetooth Disable local test' -KeyUsage DigitalSignature -KeyExportPolicy NonExportable -CertStoreLocation 'Cert:/CurrentUser/My' -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3','2.5.29.19={text}') -NotAfter (Get-Date).AddMonths(3)
# Используйте signtool.exe x64 из установленного Windows SDK:
& 'C:/Program Files (x86)/Windows Kits/10/bin/10.0.26100.0/x64/signtool.exe' sign /fd SHA256 /s My /sha1 $cert.Thumbprint $msix
Export-Certificate -Cert $cert -FilePath ./windows/artifacts/installer/BluetoothDisable-test.cer
```

На тестовом компьютере импортируйте **только этот проверенный публичный CER**
в «Локальный компьютер → Доверенные лица» (Trusted People), затем выполните
`Add-AppxPackage -Path <путь-к-подписанному-MSIX>`.
Импорт в хранилище компьютера требует отдельного административного действия;
приложение таких действий не выполняет. Не импортируйте сертификат в Trusted Root.
После теста удалите пакет и именно этот сертификат по сохранённому отпечатку.
Приватный ключ не экспортируется и не попадает в Git.

Подпись и установка MSIX в этом этапе автоматически не выполняются.
Сборка MSIX не подтверждает его установку, обновление или запуск после регистрации.

## Источники и лицензии

- [Self-contained deployment Microsoft](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/self-contained-deploy/deploy-self-contained-apps).
- [Inno Setup и лицензия](https://github.com/jrsoftware/issrc/blob/is-6_7_3/license.txt).
- [Проверка подписи Inno Setup](https://jrsoftware.org/isdl-verify.php).
- [Уведомления компонентов](./THIRD-PARTY-NOTICES.md).

Inno Setup является инструментом сборки; новых сторонних NuGet-библиотек
обнаружения или UI не добавлено. Существующий аудит NuGet сохранён.
