# YChat Android — Y-CONА edition

Нативный Android-клиент YChat на Kotlin. Он подключается к **тому же WebSocket-серверу и той же базе аккаунтов**, что и настольный Python/PySide6-клиент из исходного проекта. Это не порт Telegram APK и не копия закрытого исходного кода Telegram: используется существующий протокол YChat.

## Что уже реализовано
- Экран входа по username/email + пароль.
- Регистрация по username, имени и паролю (без email-кода; серверный сценарий с подтверждением почты пока не выведен в Android UI).
- Сохранение токена сессии и автоматическое возобновление при следующем запуске.
- Список личных чатов, групп и каналов с последними сообщениями.
- Открытие истории чата, отправка/получение сообщений по WebSocket.
- Создание групп и каналов.
- Поиск/открытие публичного чата или пользователя по @username.
- Базовое меню, настройки сервера, выход из аккаунта.
- Фирменный градиент Y-CONА (синий → сине-фиолетовый → маджента → фиолетовый), тёмный интерфейс.

## Требования
- Android Studio Ladybug или новее.
- JDK 17.
- Android SDK Platform 35.
- Интернет для загрузки Gradle и OkHttp при первой сборке.

## Сборка APK
1. Откройте папку `YChatAndroid` в Android Studio.
2. Дождитесь синхронизации Gradle.
3. Выберите **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

Командная сборка при установленном Android SDK:
```bash
gradle assembleDebug
```

## Подключение
На экране входа укажите WebSocket-адрес сервера:
- публичный сервер с TLS: `wss://your-domain.example`
- локальная сеть: `ws://192.168.1.10:8765`

Для публичного интернета используйте **только `wss://` с TLS**. `ws://` предназначен для локальной сети/тестирования. Сервер YChat должен быть запущен и доступен с телефона. Клиент Android не запускает Python-сервер на телефоне.

## Совместимость с сервером
Клиент реализует текущие действия протокола, используемые настольным приложением: `login`, `register`, `resume`, `my_chats`, `get_conv_history`, `open_handle`, `create_chat`, `send_msg`, `mark_read`. Дополнительные функции настольной версии (аватары, редактирование профиля, роли участников, значки, Bot API, email-коды, звонки и расширенные настройки) в этом первом Android-клиенте ещё не реализованы.

## Примечание о сборке в этой среде
В среде подготовки нет установленного Android SDK/Gradle, поэтому здесь подготовлен полный импортируемый Android Studio-проект, а не готовый APK. APK можно собрать указанными выше шагами.

## Mobile Telegram-inspired interface update
The Android UI now follows the familiar Telegram mobile layout patterns: compact top app bar, drawer navigation with profile header, chat rows with circular avatars and previews, floating new-chat action, conversation header with call/more actions, message bubbles, and bottom composer with attachment/send controls. Icons are drawn as vector-style Canvas line art (not emoji). Y-CONА gradient accents are retained. This is an original YChat interface inspired by common mobile messenger patterns, not Telegram's proprietary source code or assets.

## Получить APK без установки Gradle/Android SDK на своём ПК

В архив добавлен GitHub Actions workflow `.github/workflows/build-apk.yml`.
1. Создайте пустой репозиторий на GitHub.
2. Загрузите в него содержимое этой папки (файлы проекта должны лежать в корне репозитория, рядом с `settings.gradle.kts`).
3. Откройте вкладку **Actions** и запустите **Build YChat APK** через **Run workflow** (или дождитесь сборки после push).
4. После завершения откройте запуск workflow и скачайте artifact **YChat-debug-apk**.

GitHub соберёт APK на своём сервере; локально скачивать Gradle и Android SDK не потребуется. Для установки на телефон скачайте APK из artifact и откройте его на Android (разрешив установку из этого источника, если система спросит).
