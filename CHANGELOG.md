# Changelog

Все значимые изменения в проекте PhoneV2 фиксируются в этом файле.

## [6.0.0] - 2026-11-05

### Архитектура и рефакторинг
- **Модульная структура сервера**: Монолитный `server.py` (2520 строк) разбит на чистый пакет `server/` из 14 специализированных модулей (`winapi`, `capture`, `input`, `media_keys`, `windows`, `apps`, `power`, `clipboard`, `system_info`, `files`, `ota`, `network`, `auth`, `handler`).
- **Модульная структура Android**: Монолитный `MainActivity.kt` (3576 строк) разбит на раздельные файлы компонентов (`Theme.kt`, `Models.kt`, `Parsers.kt`, `Network.kt`, `Components.kt`, `PcControlMainApp.kt`, `MainActivity.kt`).
- `MainActivity.kt` сокращён с 3576 до 42 строк.
- Точка входа `server.py` сокращена с 2520 до 50 строк.

### Исправления ошибок (Bug Fixes)
- Исправлен скрытый баг с `_key_input` в UDP-сервере: вызов отсутствующей функции приводил к сбою при обработке нажатий клавиш геймпада.
- Исправлена индентация и вложенность `cachedUdpIp` и `DisposableEffect` в коде Android.
- Заменён устаревший вызов `NavTab.values()` на современный `NavTab.entries`.
- Удалён дублирующий парсинг пути URL в `do_POST` сервера.
- Устранено дублирование функции `get_ram_usage_percent`.

### Безопасность
- **Защита от Path Traversal**: Добавлена функция `is_safe_path` в файловом менеджере сервера, блокирующая UNC-пути (`\\attacker\share`) и доступ к секретам реестра (`System32\config\SAM`, `SECURITY`).
- **Network Security Config**: Создан `network_security_config.xml` для Android, принудительно запрещающий незашифрованный HTTP-трафик для внешних доменов (`trycloudflare.com`, `ntfy.sh`).
- **Изоляция секретов**: `debug.keystore`, `tunnel_url.txt` и конфигурационные файлы добавлены в `.gitignore`.

### Тестирование и документация
- Добавлен набор unit-тестов для сервера (`tests/test_server.py`) — проверка безопасности путей, структуры конфигурации, команд питания и медиа-клавиш.
- Добавлены unit-тесты для Android (`BuildPcUrlTest.kt`, `ParsersTest.kt`).
- Обновлены `README.md` и конфигурация сборки `build.gradle.kts`.
