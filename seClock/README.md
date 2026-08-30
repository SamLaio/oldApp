# seClock

`seClock` 是由舊版「時鐘小工具 白 / 黑」重建的新 Android 時鐘 Widget app。

## 目前範圍

- 目前版本：`0.1`。
- 最低版本：Android 11（API 30）。
- 白、黑兩個舊 app 合併為單一 app。
- 提供舊版 4 個 home screen Widget：Sony時鐘、Sony時鐘2、數位時鐘、指針時鐘。
- 主題支援：跟隨系統、白字版本、黑字版本。
- Widget 點擊動作支援：開啟設定、開啟時鐘、選擇開啟的 app。
- App 圖示沿用舊版時鐘圖示素材。
- Widget 保留透明底，不使用實色背景。
- Widget 選擇器使用舊版預覽圖，尺寸提示對齊 3×2、3×1、2×2。
- Widget 桌面顯示不額外顯示下一個鬧鐘列，避免比原版多佔空白。
- `Sony時鐘`、`Sony時鐘2` 改回較接近原版的正常字重。
- 不移植 Sony runtime skinning、Style Cover metadata、舊儲存空間/電話權限。

## 建置

```powershell
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleDebug
```

也可以用 Android Studio 開啟此資料夾並同步 Gradle。程式碼使用原生 Android Kotlin，沒有第三方 UI 框架。

## 簽章

release 簽章使用本機 keystore：

```text
D:\project\apkKey\oldApp.jks
```

建置 release 前先載入本機環境變數：

```powershell
. D:\project\apkKey\oldApp.local.ps1
```

## Release 輸出

```text
seClock/release/[版本]/release.apk
seClock/release/[版本]/release-notes.md
```
