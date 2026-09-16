# seClock 交接筆記

這份文件用來把 `seClock` 分出去給另一個對話繼續處理。請優先讀本檔、`README.md`、`release/0.2/release-notes.md`，再看程式碼。

## 專案定位

- app 名稱：`seClock`
- package / applicationId：`tw.idv.samliao.seclock`
- 來源目標：把舊版「時鐘小工具 白 / 黑」二合一，重建成新的原生 Kotlin Android app。
- 最低版本：Android 11，`minSdk 30`
- 目前版本：`versionCode 2`、`versionName 0.2`
- 目前 release 產物：

```text
seClock/release/0.2/release.apk
seClock/release/0.2/release-notes.md
```

## 使用者已確認的產品需求

- 使用原生 Android Kotlin，不使用跨平台框架。
- app 名稱必須是 `seClock`，要和原本舊 app 分開。
- 保留舊版兩種透明底樣式。
- 設定頁按鈕文字：
  - `舊白版樣式` 已改為 `白字版本`
  - `舊黑版樣式` 已改為 `黑字版本`
- 桌面 widget 要保留透明底。
- 需要 4 個 widget：
  - `Sony時鐘`
  - `Sony時鐘2`
  - `數位時鐘`
  - `指針時鐘`
- widget 點擊動作要可設定：
  - 開啟設定
  - 開啟時鐘
  - 選擇開啟的 app
- 選擇開啟的 app 時，要跳出 app 列表。
- widget 大小與空白佔用要貼近舊版，避免某些 widget 空白太大。
- 指針時鐘要接近原版圓形時鐘樣式。
- release 輸出要放在 app 自己的資料夾：

```text
seClock/release/[版本]/release.apk
seClock/release/[版本]/release-notes.md
```

## 重要檔案

```text
seClock/app/build.gradle
seClock/app/src/main/AndroidManifest.xml
seClock/app/src/main/kotlin/tw/idv/samliao/seclock/MainActivity.kt
seClock/app/src/main/kotlin/tw/idv/samliao/seclock/ClockWidgetUpdater.kt
seClock/app/src/main/kotlin/tw/idv/samliao/seclock/*WidgetProvider.kt
seClock/app/src/main/res/layout/widget_*.xml
seClock/app/src/main/res/xml/widget_*_info.xml
seClock/app/src/main/res/values/strings.xml
seClock/app/src/main/res/drawable-nodpi/preview_*_image.png
seClock/app/src/main/res/drawable-nodpi/analog_clock_*_image.png
```

## 目前實作摘要

- `MainActivity.kt`
  - 用純 Android view 建設定頁。
  - 儲存主題到 `SharedPreferences`。
  - 儲存 widget 點擊動作。
  - 用 `PackageManager.queryIntentActivities()` 列出 launcher apps。
- `ClockWidgetUpdater.kt`
  - 統一更新四種 widget。
  - 支援 `THEME_AUTO`、`THEME_LIGHT`、`THEME_DARK`。
  - 點擊 widget 可開設定、開系統時鐘、或開指定 app。
  - 使用 `RemoteViews.setInt(..., "setTextColor", ...)` 設定文字顏色，避免舊編譯錯誤的 `setTextViewTextColor`。
- `AndroidManifest.xml`
  - 宣告 4 個 `AppWidgetProvider` receiver。
  - 宣告 `<queries>`，讓選 app 功能能查 launcher apps。
  - 宣告 `com.android.alarm.permission.SET_ALARM`。
- widget layout
  - 均使用透明背景。
  - `clock_alarm` 大多是 `0dp` + `gone`，避免額外佔位。
  - `widget_analog.xml` 使用 `AnalogClock` 與舊版錶盤/指針圖片。

## Widget 尺寸現況

- `widget_vertical_info.xml`
  - `targetCellWidth=3`
  - `targetCellHeight=2`
  - `minWidth=180dp`
  - `minHeight=180dp`
- `widget_thin_info.xml`
  - `targetCellWidth=3`
  - `targetCellHeight=2`
  - `minWidth=180dp`
  - `minHeight=100dp`
- `widget_plain_info.xml`
  - `targetCellWidth=3`
  - `targetCellHeight=1`
  - `minWidth=180dp`
  - `minHeight=40dp`
- `widget_analog_info.xml`
  - `targetCellWidth=2`
  - `targetCellHeight=2`
  - `minWidth=110dp`
  - `minHeight=110dp`
- 四個 provider 目前都有：

```xml
android:minResizeWidth="40dp"
android:minResizeHeight="40dp"
android:resizeMode="horizontal|vertical"
android:updatePeriodMillis="1800000"
```

## 已知待確認點

- `README.md` 與 release notes 寫「隱藏下一個鬧鐘列」，layout 確實把 `clock_alarm` 設成 `gone`；但 `ClockWidgetUpdater.update()` 仍會呼叫：

```kotlin
views.setTextViewText(R.id.clock_alarm, nextAlarmText(context))
```

  目前因為 view 是 `gone` 且 `0dp`，不應佔空間。若未來刪除 `clock_alarm` view，要同步移除 updater 這行。
- 目前只靠使用者截圖做 launcher 外觀校正，沒有自動化 UI 截圖測試。
- Android launcher 對 widget cell / min size 的實際顯示會因手機 launcher 而異，調尺寸時請以使用者實機截圖為準。
- 類比時鐘黑色版本目前用 `widget_analog_black.xml` 與黑色 drawable tint，請確認 `ClockWidgetUpdater.themedLayoutId()` 是否符合使用者期望。

## 建置與簽章

Debug：

```powershell
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleDebug
```

Release 前載入簽章：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleRelease
```

Gradle release APK 輸出：

```text
seClock/app/build/outputs/apk/release/release.apk
```

依 repo 慣例要複製到：

```text
seClock/release/[版本]/release.apk
```

## Repo 慣例提醒

- 文件使用正體中文。
- 不要提交 keystore、簽章密碼、`local.properties`、`key.properties`、APK、AAB、ZIP、EXE 或 build cache。
- 未取得當前回合明確授權，不要執行 `git add`、`git commit`、`git push`、tag 或 GitHub release。
- 產 APK 前要先檢查目前版本是否已發過 release；若已發過且有新功能或使用者可見變更，要先升版本。

## 下一個對話建議起手式

1. 先讀：

```text
D:\github\oldApp\seClock\HANDOFF.md
D:\github\oldApp\seClock\README.md
D:\github\oldApp\seClock\release\0.2\release-notes.md
```

2. 若使用者要調外觀，先看：

```text
seClock/app/src/main/res/layout/widget_*.xml
seClock/app/src/main/res/xml/widget_*_info.xml
```

3. 若使用者要調設定或點擊行為，先看：

```text
seClock/app/src/main/kotlin/tw/idv/samliao/seclock/MainActivity.kt
seClock/app/src/main/kotlin/tw/idv/samliao/seclock/ClockWidgetUpdater.kt
```
