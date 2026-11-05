# PhoneV2 — «Пульт ПК»

Android-приложение (Jetpack Compose) + Python-сервер для Windows 11: управление ПК с телефона.

Возможности: стрим экрана (MJPEG до 90 FPS), мышь (UDP 125 Гц) и клавиатура, виртуальный геймпад, буфер обмена, запуск и управление окнами программ, проводник файлов ПК, выключение/сон/таймеры, Wake-on-LAN, автозагрузка фото с телефона на ПК, обновление приложения по воздуху (OTA).

## Схема

```
Телефон  ──(Tailscale / Wi-Fi / Cloudflare-туннель)──►  server.py на ПК (порт 8765, TCP и UDP)
```

## Установка сервера (ПК)

1. Python 3.10+ для Windows (без сторонних библиотек, только стандартная библиотека).
2. Запусти `install_autostart.bat` — он добавит сервер в автозагрузку и откроет порты в брандмауэре только для локальной сети и Tailscale (100.64.0.0/10).
3. Токен и настройки лежат в `config.json` (создаётся при первом запуске, в git не попадает).
4. Лог — `server.log`.

## Установка приложения

APK собирает GitHub Actions (вкладка Actions → «Build APK» → артефакт `pc-control-apk`).
Положи APK в папку `updates/` рядом с `server.py`, и приложение само предложит обновиться.
Подробности и настройка своего ключа подписи — в [BUILD.md](BUILD.md).

## Архитектура проекта

### Сервер (`server/`)
- `server/winapi.py` — WinAPI структуры, константы и GDI+ инициализация через `ctypes`
- `server/capture.py` — захват экрана и сжатие в JPEG памяти с отрисовкой курсора
- `server/input.py` — эмуляция мыши, клавиатуры и UTF-16 ввода через `SendInput`
- `server/media_keys.py` — управление громкостью, системными медиа-клавишами и плеером YouTube
- `server/windows.py` — перечисление окон, фокус, сворачивание и управление
- `server/apps.py` — запуск и завершение программ, добавление пользовательских приложений
- `server/power.py` — выключение, перезагрузка, сон, таймеры и выключение монитора
- `server/clipboard.py` — чтение и запись системного буфера обмена Windows
- `server/system_info.py` — телеметрия CPU, RAM, накопителей, аптайма и громкости
- `server/files.py` — проводник файлов, скачивание и загрузка фото с защитой от Path Traversal
- `server/ota.py` — парсер бинарного AXML в APK и проверка OTA-обновлений
- `server/network.py` — UDP мышь/геймпад (125 Гц), UPnP, Wi-Fi Discovery и Cloudflare-туннель
- `server/auth.py` — авторизация по HMAC-токену с защитой от брутфорса (блокировка на 15 мин)
- `server/handler.py` — HTTP/MJPEG роутер (`ThreadingHTTPServer`)
- `server.py` — лаконичная точка входа

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

Запуск тестов сервера:
```bash
python -m unittest discover tests
```

Запуск тестов Android:
```bash
gradle test
```

## Синхронизация с GitHub

`sync_github.bat` делает commit → `git pull --rebase` → push. Принудительный push не используется.
