# Device Owner QR provisioning

## Назначение

Bluetooth Disable использует Android Device Policy и может работать как Device Policy Controller (DPC).

Для системной блокировки Bluetooth приложение должно быть назначено **Device Owner**. Один из поддерживаемых способов назначения — QR provisioning через Android Setup Wizard во время первоначальной настройки устройства.

Полная пошаговая инструкция для **QR** и **ADB** опубликована на сайте проекта:

https://alcovaibe.github.io/Bluetooth-disabler/

## Требования

Для QR provisioning необходимо:

- Android 8.0 (API 26) или новее;
- поддержка Device Owner provisioning со стороны устройства и прошивки;
- первоначальная настройка Android;
- доступ к сети для загрузки APK;
- отсутствие уже назначенного несовместимого Device Owner или другого состояния, блокирующего provisioning.

На большинстве устройств QR provisioning используется после сброса к заводским настройкам. Конкретные условия и способ открытия режима QR зависят от версии Android и производителя устройства.

Перед сбросом устройства необходимо сохранить важные данные.

## Что содержит QR-код

QR-код содержит стандартный Android provisioning payload со следующими данными:

- компонент Device Admin приложения;
- URL для загрузки APK;
- контрольную сумму APK.

Используемые поля:

```text
android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM
```

Provisioning payload не содержит пользовательских данных, паролей или ключей подписи приложения.

## Проверка целостности APK

Для опубликованного APK вычисляется SHA-256. Контрольная сумма преобразуется в URL-safe Base64 и включается в provisioning payload.

Android Setup Wizard использует эту контрольную сумму для проверки загруженного APK перед назначением Device Owner.

Контрольная сумма привязана к конкретным байтам APK. Если APK пересобран или изменён, для него требуется новая контрольная сумма и новый QR-код.

## Актуальный QR

Стабильный QR-код последней опубликованной версии хранится в:

```text
docs/bluetooth-disable-device-owner-qr.png
```

Соответствующий provisioning JSON:

```text
docs/bluetooth-disable-device-owner-provisioning.json
```

Оба файла автоматически обновляются при публикации нового релиза и используются корневым README и сайтом проекта.

## Совместимость

Поведение Device Owner provisioning зависит от:

- версии Android;
- производителя устройства;
- реализации Android Setup Wizard;
- корпоративных ограничений прошивки;
- уже выполненной первоначальной настройки устройства.

На отдельных устройствах QR provisioning может быть недоступен или запускаться иначе, чем на стандартном Android.

Если QR provisioning недоступен, альтернативный способ назначения через ADB описан на сайте проекта.

## Дополнительная информация

Исходный код release workflow, который формирует provisioning JSON, вычисляет SHA-256 и генерирует QR-код:

[`.github/workflows/release.yml`](../.github/workflows/release.yml)

Полные пользовательские инструкции по настройке Device Owner через QR и ADB:

https://alcovaibe.github.io/Bluetooth-disabler/
