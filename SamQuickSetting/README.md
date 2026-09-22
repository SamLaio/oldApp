# SamQuickSetting

`SamQuickSetting` 是由舊版 `Quicker` 概念重建的原生 Android Kotlin 懸浮快速設定面板 app。

## 現況

- app 名稱：`SamQuickSetting`
- package / applicationId：`tw.idv.samliao.quick.setting`
- 版本：`0.9`
- `versionCode`：`9`
- 最低版本：Android 10，`minSdk 29`
- `targetSdk` / `compileSdk`：`35`
- 技術：原生 Android Kotlin，無第三方 UI 框架。

## 已實作功能

- 桌面圖示啟動後直接開啟懸浮面板。
- 設定頁可：
  - 開啟懸浮面板
  - 側邊條設定：顯示 / 隱藏、寬度與長度
  - 加入設定鈕
  - 加入應用程式
  - 選擇面板背景
  - 選擇格數
  - 鎖定 / 解鎖面板
  - 開啟 / 關閉 debug log，預設關閉
  - 清空面板內容
- 側邊條可拖曳，放手後吸附螢幕左右邊，點擊才開啟懸浮面板。
- 面板資料已改為 `active_panel` / `panel_count` 架構；目前預設只有第一個面板。
- 格數與鎖定狀態為所有面板共用設定，項目、背景與位置則跟著各面板獨立保存。
- 懸浮面板使用 `TYPE_APPLICATION_OVERLAY`。
- 面板格數：
  - `3 x 4`
  - `4 x 5`
  - `5 x 6`
- 面板背景：
  - 黑色點紋
  - 灰色點紋
  - 藍色點紋
  - 紫色點紋
  - 藍色泡泡
  - 綠色泡泡
- 面板項目可拖曳排列。
- 長按面板項目後可拖到刪除球移除。
- 亮度與音量使用跨整列滑桿。
- 「應用程式」以 package 開啟 App。

## 可加入的工具

系統設定：

```text
亮度
音量
鈴聲/震動模式
螢幕逾時
自動旋轉
自動同步
Wi-Fi 設定
Bluetooth 設定
位置設定
Wi-Fi 熱點設定
NFC 設定
```

指示器：

```text
儲存空間資訊
電量
```

工具：

```text
手電筒
應用程式
```

## 權限與限制

- 需要「顯示在其他應用程式上層」才能顯示懸浮面板或側邊條。
- 亮度、螢幕逾時、自動旋轉需要「允許修改系統設定」。
- 鈴聲／震動模式首次使用可能要求「勿擾模式存取權」。
- 手電筒需要相機權限。
- Wi-Fi、Bluetooth、位置、熱點、NFC 等在現代 Android 上多數不能直接靜默切換，因此目前多數是開啟對應系統設定頁或顯示狀態。
- 不移植舊版直接切行動資料、飛航模式、4G/WiMAX、清全 app cache、讀系統 log、把第三方桌面 widget 放進面板等功能。

## 重要檔案

```text
app/src/main/kotlin/tw/idv/samliao/quick/setting/MainActivity.kt
app/src/main/kotlin/tw/idv/samliao/quick/setting/FloatingPanelService.kt
app/src/main/kotlin/tw/idv/samliao/quick/setting/PanelHandleService.kt
app/src/main/kotlin/tw/idv/samliao/quick/setting/PanelLauncherActivity.kt
app/src/main/kotlin/tw/idv/samliao/quick/setting/PanelConfig.kt
app/src/main/kotlin/tw/idv/samliao/quick/setting/QuickActions.kt
app/src/main/AndroidManifest.xml
```

## 建置

```powershell
cd D:\github\oldApp\SamQuickSetting
.\gradlew.bat :app:assembleDebug
```

Release：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\SamQuickSetting
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
SamQuickSetting/release/[版本]/release.apk
SamQuickSetting/release/[版本]/release-notes.md
```
