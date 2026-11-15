# FunPay Tools — iOS

Порт Android-приложения FunPay Tools на iPhone/iPad (iOS 15+).

## Установка

1. Скачай `FunPayTools.ipa` из [Releases](../../releases/latest).
2. Подпиши и установи любым способом:
   - **Sideloadly** (Windows/macOS) с Apple ID; бесплатный аккаунт продлевается раз в 7 дней;
   - **AltStore / SideStore**;
   - **ESign / Scarlet / Feather** с собственным сертификатом;
   - **TrollStore**, если поддерживается твоей версией iOS.
3. При первом запуске разреши уведомления и включи «Фоновый режим» на экране «Доступы».

> На бесплатном Apple ID виджеты занимают ещё один App ID (лимит 3 в неделю). Если не хватает,
> в Sideloadly включи «Remove app extensions».

## Что работает иначе, чем на Android

| Android | iOS |
|---|---|
| Foreground-сервис 24/7 | Работа в фоне через беззвучный аудиосеанс (переключатель «Фоновый режим»). Не закрывай приложение свайпом. |
| Ответ из уведомления | Есть (поле ввода в уведомлении) |
| Полноэкранный «звонок» о заказе | Уведомление с повышенным приоритетом (Time Sensitive) |
| Виджеты рабочего стола | Виджеты WidgetKit «Баланс» и «Профиль» |
| JS-плагины (WebView) | JavaScriptCore, тот же API `fpt.*` |
| Реклама Start.io | Нет SDK: функции, которые открываются рекламой, открываются сразу (как при ошибке загрузки рекламы на Android) |
| Оптимизация батареи / автозапуск | Не нужно, экран заменён советами для iPhone |

## Сборка

Собирает GitHub Actions (`.github/workflows/build-ipa.yml`) на macOS-раннере. Каждый пуш в `main`
публикует новый релиз с неподписанным `.ipa`.

Локально, нужен Mac с Xcode 16+:

```bash
./gradlew :shared:linkReleaseFrameworkIosArm64
brew install xcodegen && (cd iosApp && xcodegen generate)
open iosApp/FunPayTools.xcodeproj
```

## Устройство проекта

- `shared/src/commonMain/kotlin/ru/allisighs/funpaytools/` — код Android-приложения почти без изменений
  (Compose Multiplatform).
- `shared/src/commonMain/.../compat/` — слой совместимости с Android API: OkHttp, Retrofit, org.json,
  Gson, SharedPreferences, Intent/Uri, уведомления, WebView, android.graphics на Skia, Firebase REST,
  java.util/java.io.
- `shared/src/iosMain/` — реализации на iOS: NSURLSession, UNUserNotificationCenter, WKWebView,
  PHPicker/UIDocumentPicker, Keychain, CoreMotion, AVAudioSession.
- `iosApp/` — Swift-обвязка: AppDelegate, корневой контроллер (статус-бар, ориентация), мост
  JavaScriptCore для плагинов, WidgetKit-расширение.
- `shared/src/jvmMain/` — desktop-цель для быстрой проверки UI без Mac
  (`./gradlew :shared:snapshot -PsnapArgs=dashboard+settings` рендерит экраны в `/tmp/snaps`).
