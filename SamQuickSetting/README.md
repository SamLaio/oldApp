# SamQuickSetting

`SamQuickSetting` 是由舊版 `Quicker 1.9.3` 解包檔重新建立的原生 Android Kotlin 懸浮快速面板 app。

## 舊版解包分析

- 來源資料夾：`D:\github\oldApp\fix\quick`
- 舊 package：`com.bwx.quicker`
- 舊 app 名稱：`Quicker`
- 舊版本：`versionCode 21`、`versionName 1.9.3`
- 舊 SDK：`minSdk 7`、`targetSdk 10`
- 主要入口：`com.bwx.quicker.ui.SwitchesActivity`
- 舊版核心是自訂快速開關面板與多頁格子 workspace，不是標準 Android AppWidget。
- 舊版包含 Wi-Fi、Bluetooth、GPS、行動資料、飛航模式、熱點、4G/WiMAX、亮度、音量、鈴聲、電池、時鐘、快取清理、應用程式捷徑等 23 種項目。

## 相容性取捨

- 舊版直接切換 Wi-Fi、Bluetooth、GPS、行動資料、飛航模式與 Wi-Fi 熱點的做法，在現代 Android 上多數已被限制，一般 app 不適合照舊移植。
- 舊版清除所有 app cache、讀取系統 log、WiMAX / 4G 直接切換等功能已不適合作為新版發版功能。
- 新版保留舊版核心概念：建立像桌面一樣的懸浮快捷面板，再把設定鈕或 app 捷徑放到面板上。
- 受現代 Android 權限制約的功能會改成開啟對應系統設定頁，避免做出看得到但不能正常工作的按鈕。

## 目前範圍

- package：`tw.idv.samliao.quick.setting`
- 目前版本：`0.1`
- 最低版本：Android 10（API 29）。
- 可建立接近滿版的系統 overlay 懸浮面板，第一次使用需授權「顯示在其他應用程式上層」。
- 懸浮面板外層不再額外套底色，只保留選定的面板背景圖樣。
- 點選桌面上的 app 圖示會直接呼叫懸浮面板，不進入設定畫面。
- 懸浮面板底部提供「設定面板」與「關閉面板」按鈕；設定畫面提供「確定」按鈕用來離開。
- 可從「系統設定、指示器、工具」三組選單加入工具。
- 可把設定鈕加入懸浮面板：亮度、音量、鈴聲/震動模式、螢幕逾時、自動旋轉、自動同步、Wi-Fi、Bluetooth、位置、Wi-Fi 熱點。
- 資訊類只保留簡單儲存空間顯示。
- 工具類保留手電筒與應用程式捷徑。
- 應用程式捷徑會列出目前已安裝且有桌面入口的 app，選好後直接加入面板，可加入多個不同 app。
- 懸浮面板會直接以大型浮動視窗開啟，內部像小型桌面一樣可拖曳排列工具。
- 可設定面板格數，目前提供 `3 x 4`、`4 x 5` 與 `5 x 6`，按鈕大小與吸附位置會跟著格數變化。
- 設定按鈕拖曳放手後會自動吸附到格線，保持整齊排列。
- 面板按鈕會避開已佔用格子；拖到已佔用格時，原本的按鈕會移到最近空格，格數容量已滿時不再新增。
- 長按面板上的按鈕可拖曳移位，旁邊會顯示浮動刪除球；拖到刪除球放開才會移除按鈕。
- 工具按鈕改為上方圖示、中間狀態或 app 名稱；只有單純開關類顯示底部狀態線，啟用中為綠線，未啟用為灰線。
- 亮度與音量這類滑桿工具會把數值放在圖示旁邊；亮度狀態線表示是否開啟自動亮度，音量不顯示狀態線。
- 亮度與音量會以跨整列的 widget 樣式顯示滑桿，可直接拖曳調整；點按鈕本體仍可循環調整級距。
- 亮度點按可在手動亮度級距與自動亮度間切換。
- 可選擇沿用舊版 app 圖樣素材的面板背景，例如黑/灰/藍/紫點紋與藍/綠泡泡背景。
- Wi-Fi、Bluetooth、位置與 Wi-Fi 熱點採用開啟系統設定頁；若裝置沒有直接熱點設定頁，會退回無線網路設定頁。
- Wi-Fi 面板按鈕會顯示連線狀態線與可讀取到的 SSID；Android 10 以上若未授權位置權限，SSID 會退回顯示 `Wi-Fi`。
- 亮度、螢幕逾時與自動旋轉需要 Android 的「允許修改系統設定」授權。
- 手電筒需要相機權限。
- 不保留桌面 Widget、把第三方桌面 Widget 放進面板、行動資料、飛航模式、4G/WiMAX、全 app 快取清理與讀取系統 log。
- release APK 固定輸出為 `SamQuickSetting/release/[版本]/release.apk`
- release notes 固定輸出為 `SamQuickSetting/release/[版本]/release-notes.md`

## 建置

Debug 建置：

```powershell
cd D:\github\oldApp\SamQuickSetting
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
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
SamQuickSetting/release/[版本]/release.apk
SamQuickSetting/release/[版本]/release-notes.md
```
