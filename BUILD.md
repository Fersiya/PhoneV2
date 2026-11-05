# Сборка приложения

## Вариант 1. GitHub Actions (основной)

Каждый push в `main` (кроме `*.md`, `server.py`, `*.bat`) запускает сборку.
Готовый APK: Actions → последний запуск → артефакт `pc-control-apk`.

### Свой ключ подписи (один раз)

Без ключа CI собирает debug-APK, у которого подпись меняется от сборки к сборке — обновление «поверх» может не пройти.
Чтобы подпись была постоянной:

1. Создай ключ: `keytool -genkeypair -v -keystore pccontrol-release.jks -alias pccontrol -keyalg RSA -keysize 2048 -validity 10000`
2. В репозитории: Settings → Secrets and variables → Actions → New repository secret:
   - `RELEASE_KEYSTORE_B64` — содержимое файла в base64 (`base64 -w0 pccontrol-release.jks`)
   - `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
3. Сам `.jks` в репозиторий **не коммить** (он в `.gitignore`) и сохрани резервную копию: потеряешь — придётся переустанавливать приложение.

Версия растёт автоматически: `versionCode = 100 + номер запуска Actions`.

## Вариант 2. Android Studio

File → Open → папка проекта, дождись Gradle Sync, Run ▶ на телефоне с включённой отладкой по USB.
Gradle wrapper в репозитории пока нет (CI ставит Gradle 8.9 сам); Studio при открытии предложит свою версию.

## Если сборка падает

Скопируй текст ошибки (в Studio — вкладка Build, на GitHub — лог упавшего шага) и пришли его.
