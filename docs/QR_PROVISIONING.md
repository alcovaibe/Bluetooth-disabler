# Device Owner QR provisioning

## Общая схема

Bluetooth Disable распространяется как Device Policy Controller. Для полностью автоматизированного provisioning используется QR-код Android Setup Wizard.

Начиная с текущего release pipeline все новые релизы используют теги строго в формате:

```text
v<version>
```

Например:

```text
v1.0.20
```

Тег должен точно соответствовать `versionName` из `app/build.gradle.kts`. Workflow отклоняет релиз, если тег и `versionName` расходятся.

## Автоматическая генерация QR

Workflow `.github/workflows/release.yml` выполняет последовательность:

1. собирает подписанный release APK;
2. публикует GitHub Release;
3. вычисляет SHA-256 именно опубликованного APK;
4. преобразует digest в URL-safe Base64 без padding;
5. формирует provisioning JSON;
6. генерирует QR-код;
7. прикладывает JSON и PNG к GitHub Release;
8. обновляет стабильные файлы в ветке `main`:
   - `docs/bluetooth-disable-device-owner-qr.png`;
   - `docs/bluetooth-disable-device-owner-provisioning.json`.

Таким образом QR всегда привязан к точным байтам подписанного APK конкретного релиза.

## Формат release asset

Release APK получает имя:

```text
BluetoothDisable-v<version>.apk
```

Для `v1.0.20`:

```text
BluetoothDisable-v1.0.20.apk
```

Provisioning download URL строится из текущего GitHub tag и имени asset.

## Provisioning payload

Автоматически формируется JSON следующей структуры:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.pulse.bluetoothdisable/.admin.AppDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/alcovaibe/Bluetooth-disabler/releases/download/v<version>/BluetoothDisable-v<version>.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "<URL_SAFE_BASE64_SHA256>"
}
```

Checksum нельзя переносить между сборками: любое изменение APK меняет digest и требует нового QR.

## Файлы релиза

После успешного workflow GitHub Release содержит:

- `BluetoothDisable-v<version>.apk`;
- `bluetooth-disable-device-owner-qr.png`;
- `bluetooth-disable-device-owner-provisioning.json`.

Стабильный QR в `docs/bluetooth-disable-device-owner-qr.png` обновляется автоматически и предназначен для README и обычного provisioning последней опубликованной версии.

Старый файл `docs/bluetooth-disable-1.0.6-device-owner-qr.png` оставлен только как исторический артефакт предыдущего release flow и не должен использоваться для новых установок после публикации нового `v*` релиза.

## QR на сайте

В карточке 01 сайта `docs/pages/` приведены пошаговые действия. Кнопка
«Показать QR» находится в последнем шаге и открывает диалог.

При каждом открытии сайт запрашивает последний опубликованный релиз через
GitHub API и показывает его `bluetooth-disable-device-owner-qr.png`.
Обновлять HTML или повторно публиковать сайт после релиза не требуется.
Если PNG ещё не опубликован или недоступен, сайт формирует QR локально из
ссылки на APK этого же релиза и его `sha256` digest из GitHub API. Контрольная
сумма преобразуется в URL-safe Base64 без padding, как в release workflow.
Диалог показывает версию APK; внешний сервис генерации QR не используется.

Если получить текущий релиз или корректную контрольную сумму невозможно,
сайт показывает ошибку и кнопку повторной загрузки вместо устаревшего QR.

## Ручная проверка перед релизом

Перед созданием production tag рекомендуется убедиться, что:

- `versionName` и `versionCode` обновлены;
- CI на PR зелёный;
- `main` зелёный на Android Full Compatibility;
- release secrets содержат актуальный signing keystore и пароли.

После публикации релиза рекомендуется один раз проверить новый QR на физическом устройстве после factory reset: Setup Wizard должен скачать APK, проверить checksum и назначить Bluetooth Disable Device Owner.
