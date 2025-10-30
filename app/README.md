# AI Chat Voice (Android)

Голосовой чат-клиент на Jetpack Compose с интеграцией n8n Webhook, голосовым вводом/выводом и анимированным Voice Orb.

## Сборка и запуск

1. Убедитесь, что установлен Android Studio Ladybug или новее (AGP 8.5+).
2. Откройте корневую папку проекта в Android Studio и выполните Gradle Sync.
3. Запустите `app` на устройстве/эмуляторе (минимум Android 8.0, API 26).
4. При первом запуске разрешите доступ к микрофону для голосового режима.

### CLI

```bash
./gradlew assembleDebug
```

## n8n Webhook конфигурация

URL и режим задаются через `BuildConfig`:

- `BuildConfig.N8N_TEST_URL`
- `BuildConfig.N8N_PROD_URL`
- `BuildConfig.N8N_MODE` (`test` или `prod`)

По умолчанию режим — `test`. Чтобы собрать с продовым хуком, передайте свойство Gradle:

```bash
./gradlew assembleRelease -PN8N_MODE=prod
```

Изменить значения URL можно в `app/build.gradle.kts` (секция `defaultConfig`).

## Архитектура

- **DI**: Hilt (`App.kt`, `di/AppModule.kt`).
- **Networking**: Retrofit + kotlinx.serialization (строго JSON, заголовок `Content-Type: application/json`).
- **Domain**: `domain/model`, `domain/repo`, `domain/usecase`.
- **UI**: Compose (экран чата, composer, voice overlay, markdown-рендерер).
- **Voice**: SpeechRecognizer (ASR), TextToSpeech (TTS), собственные VAD и FFT/AudioAnalyzer.
- **Графика**: OpenGL ES 2.0 Voice Orb (шейдеры из `res/raw`), Canvas fallback.

## Тестирование

- Unit/UI тесты могут быть добавлены в `app/src/test` и `app/src/androidTest` соответственно.
- Для проверки работы webhook можно использовать дебаг-режим (`BuildConfig.N8N_MODE = test`).

## Разрешения

Приложение запрашивает `RECORD_AUDIO` во время работы голосового режима и отображает обоснование при отказе.
