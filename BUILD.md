# Сборка приложения PC Control

Это готовый проект Android Studio. Код экрана лежит в
`app/src/main/java/com/example/pccontrol/MainActivity.kt`.

## Вариант 1. Android Studio

1. Распакуй архив в любую папку.
2. Android Studio → File → Open → выбери папку `pc-control-app`.
3. Дождись окончания Gradle Sync (первый раз он скачивает зависимости, это несколько минут).
   Если Studio предложит обновить версии Gradle или плагина, соглашайся.
4. Подключи телефон по USB (включи «Отладку по USB») и нажми Run ▶.
   Или Build → Build Bundle(s) / APK(s) → Build APK(s), готовый файл будет в `app/build/outputs/apk/debug/app-debug.apk`.

## Вариант 2. APK в облаке через GitHub (без Android Studio)

1. Создай аккаунт на github.com и новый репозиторий (можно приватный), ветка `main`.
2. Загрузи в него всё содержимое папки проекта, включая скрытую папку `.github`
   (на странице репозитория: Add file → Upload files, перетащи файлы и папки).
3. Открой вкладку Actions → «Build APK» → при необходимости нажми Run workflow.
4. Через 3–6 минут в запуске появится артефакт `pc-control-apk`. Скачай, достань из архива `app-debug.apk`.
5. Перенеси APK на телефон и установи (Android спросит разрешение на установку из неизвестных источников).

## Если сборка падает

Скопируй текст ошибки (в Android Studio вкладка Build, на GitHub лог шага «Build debug APK») и пришли его.
