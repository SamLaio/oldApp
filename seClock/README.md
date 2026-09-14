# seClock

`seClock` 是由舊版「時鐘小工具 白 / 黑」重建的新 Android 時鐘 Widget app。

## 現況

- app 名稱：`seClock`
- package / applicationId：`com.samliao.seclock`
- 版本：`0.1`
- `versionCode`：`1`
- 最低版本：Android 11，`minSdk 30`
- `targetSdk` / `compileSdk`：`35`
- 技術：原生 Android Kotlin，無第三方 UI 框架。

## 已實作功能

- 白、黑兩個舊 app 合併成單一 app。
- 提供 4 個 home screen widget：
  - `Sony時鐘`
  - `Sony時鐘2`
  - `數位時鐘`
  - `指針時鐘`
- Widget 背景保持透明。
- Widget 選擇器使用舊版預覽圖。
- 主題設定：
  - 跟隨系統
  - 白字版本
  - 黑字版本
- Widget 點擊動作設定：
  - 開啟設定
  - 開啟時鐘
  - 選擇開啟的 app
- 選擇 app 時會列出 launcher apps，並把選定 package 存進設定。
- app 圖示已補上。

## Widget 尺寸與版面

- `Sony時鐘`：目標 `3 x 2`
- `Sony時鐘2`：目標 `3 x 2`
- `數位時鐘`：目標 `3 x 1`
- `指針時鐘`：目標 `2 x 2`
- provider 皆支援水平與垂直 resize。
- `minResizeWidth` / `minResizeHeight` 目前皆為 `40dp`。
- `clock_alarm` view 目前保留但設為 `gone` 與 `0dp`，避免佔桌面空間。

## 重要檔案

```text
app/src/main/kotlin/com/samliao/seclock/MainActivity.kt
app/src/main/kotlin/com/samliao/seclock/ClockWidgetUpdater.kt
app/src/main/kotlin/com/samliao/seclock/*WidgetProvider.kt
app/src/main/res/layout/widget_*.xml
app/src/main/res/xml/widget_*_info.xml
app/src/main/res/values/strings.xml
HANDOFF.md
```

## 待確認

- `ClockWidgetUpdater` 仍會填入 `clock_alarm` 文字；目前 layout 隱藏該 view，所以不會顯示。若未來刪掉 `clock_alarm`，要同步移除 updater 對 `R.id.clock_alarm` 的操作。
- widget 實際格數與空白佔用受 launcher 影響，需以使用者實機截圖校正。
- `指針時鐘` 黑色版本目前用 `widget_analog_black.xml`。

## 建置

```powershell
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleDebug
```

Release：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
seClock/release/[版本]/release.apk
seClock/release/[版本]/release-notes.md
```
