# PhoneV2 — «Пульт ПК»

Android-приложение (Jetpack Compose) + Python-сервер для Windows 11: управление ПК с телефона.

Возможности: стрим экрана (MJPEG до 90 FPS), мышь (UDP 125 Гц) и клавиатура, виртуальный геймпад, буфер обмена, запуск и управление окнами программ, проводник файлов ПК, выключение/сон/таймеры, Wake-on-LAN, автозагрузка фото с телефона на ПК, обновление приложения по воздуху (OTA).

## Схема

```
Телефон  ──(Tailscale / Wi-Fi / Cloudflare-туннель)──►  сервер на ПК (порт 8765, TCP и UDP)
```

## Сервер (ПК)

Сервер живёт в отдельном репозитории: [PhoneV2-server](https://github.com/Fersiya/PhoneV2-server).
Он слушает порт 8765 (TCP и UDP); токен и настройки лежат в его `config.json`.

## Установка приложения

APK собирает GitHub Actions (вкладка Actions → «Build APK» → артефакт `pc-control-apk`).
Положи APK в папку `updates/` рядом с сервером, и приложение само предложит обновиться.
Подробности и настройка своего ключа подписи — в [BUILD.md](BUILD.md).

## Архитектура проекта


### Android (`app/src/main/java/com/example/pccontrol/`)
- `Theme.kt` — тёмная кибер-палитра (Midnight Navy + Electric Cyan)
- `Models.kt` — модели данных, статусы, профили ПК и перечисление вкладок `NavTab`
- `Parsers.kt` — парсинг JSON-телеметрии, URL-билдер и SHA-256 хеширование
- `Network.kt` — HTTP/UDP клиент, высокоскоростной MJPEG декодер и Wake-on-LAN
- `Components.kt` — кнопки управления, сенсорный скролл и геймпадные клавиши
- `PcControlMainApp.kt` — навигация и главный экран приложения
- `MainActivity.kt` — Activity с поддержкой отправки ссылок (Share Intent)
- `PcControlTileService.kt` — шторка быстрых настроек Android для мгновенного Wake-on-LAN

## Безопасность (модель угроз)

- Доступ защищён токеном (заголовок `X-Token` с HMAC-валидацией). После 10 неверных попыток IP блокируется на 15 минут.
- Защита от Path Traversal: блокировка сетевых UNC-путей и запрет доступа к системным секретам Windows (`System32\config\SAM` и др.).
- Сетевая безопасность Android (`network_security_config.xml`): открытый трафик разрешён только для локальной сети и Tailscale, для внешних туннелей (`trycloudflare.com`) принудительно включён HTTPS.
- Не пробрасывай порт 8765 на роутере напрямую в интернет.

## Тестирование

Тесты сервера — в репозитории PhoneV2-server. Тесты Android:
```bash
gradle test
```

## Синхронизация с GitHub

`sync_github.bat` делает commit → `git pull --rebase` → push. Принудительный push не используется.
