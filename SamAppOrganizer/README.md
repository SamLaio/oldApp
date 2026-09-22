# SamAppOrganizer

`SamAppOrganizer` 是用「資料夾關鍵字」整理已安裝 launcher app 的原生 Android Kotlin app。

## 現況

- app 名稱：`SamAppOrganizer`
- package / applicationId：`tw.idv.samliao.apporganizer`
- 版本：`0.3`
- `versionCode`：`3`
- 最低版本：Android 10，`minSdk 29`
- `targetSdk` / `compileSdk`：`35`
- 技術：原生 Android Kotlin，無第三方 UI 框架。

## 已實作功能

- 掃描可啟動的已安裝 app。
- 主畫面可手動重新整理，重新掃描 app、重新歸類並更新桌面小工具。
- 用 SQLite 保存資料夾、關鍵字、全域圖示風格與手動分類，方便後續搬移與備份。
- 依資料夾關鍵字、Android app category 與 package/app 名稱自動分類。
- 資料夾列表會顯示資料夾預覽圖示。
- 分類內 app 以類 launcher 清單顯示 app 圖示、名稱與 package。
- 長按資料夾可改名稱與關鍵字。
- 編輯既有資料夾時可選擇 app；清單會勾選目前在資料夾內的 app。
- 主畫面可選全域資料夾圖示風格，預設以透明底顯示 app 圖示；所有資料夾與桌面小工具會同步套用。
- 主畫面可輸入名稱或 package 篩選所有 app，並可直接長按結果調整分類。
- 可新增自訂資料夾，例如建立 `Google` 並加入 `google, gmail, youtube, maps`。
- 點資料夾可看該分類 app。
- 長按 app 可手動指定分類，手動指定優先於自動分類。
- 變更 app 分類或資料夾設定後，會留在原本開啟的頁面。
- 桌面小工具是 `1 x 1` 資料夾圖示，新增時先選資料夾。
- 點桌面資料夾小工具會展開資料夾 popup，點 app 圖示可啟動 app。
- 展開後長按標題可進入 app 主畫面。
- 內層返回回到上一層；最外層按系統返回才關閉 app。

## 權限與限制

- 只查詢有 launcher 入口的 app。
- 第一版不查 Google Play 線上分類，也不讀 Play 商店說明。
- 桌面 Widget 的展開 popup 目前顯示前 9 個 app，尚未做可捲動完整列表。

## 重要檔案

```text
app/src/main/kotlin/tw/idv/samliao/apporganizer/MainActivity.kt
app/src/main/kotlin/tw/idv/samliao/apporganizer/AppOrganizerDb.kt
app/src/main/kotlin/tw/idv/samliao/apporganizer/FolderWidgetProvider.kt
app/src/main/kotlin/tw/idv/samliao/apporganizer/FolderWidgetConfigActivity.kt
app/src/main/kotlin/tw/idv/samliao/apporganizer/FolderPopupActivity.kt
app/src/main/AndroidManifest.xml
```

## 建置

```powershell
cd D:\github\oldApp\SamAppOrganizer
.\gradlew.bat :app:assembleDebug
```

Release：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\SamAppOrganizer
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
SamAppOrganizer/release/[版本]/release.apk
SamAppOrganizer/release/[版本]/release-notes.md
```
