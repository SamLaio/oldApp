# SamMiniCam

`SamMiniCam` 是 Canon Mini Cam / FV-100 的 Android 10+ 原生 Kotlin 硬體連線測試 app。它目前不是完整相機 app，重點是測 BLE、相機 Wi-Fi、HTTP 與 RTSP 入口。

## 現況

- app 名稱：`SamMiniCam`
- package / applicationId：`cam.sam.minicam`
- 版本：`0.1`
- `versionCode`：`1`
- 最低版本：Android 10，`minSdk 29`
- `targetSdk` / `compileSdk`：`35`
- 技術：原生 Android Kotlin，無第三方 UI 框架。

## 已實作功能

- runtime 權限檢查。
- BLE 掃描，列出裝置名稱、MAC 與 RSSI。
- 點擊掃描結果後嘗試 BLE GATT 連線並列出 GATT services。
- 可手動輸入 `BT MAC` 直接嘗試 BLE GATT 連線。
- 可輸入 12 碼相機序號，自動推算：
  - SSID：`Canon {顏色碼} {序號末四碼}`
  - 密碼：`MD5(序號)` 最後 8 碼
- 可輸入 `WIFI MAC` 作為參考，並自動推算 `BT MAC = WIFI MAC + 1`。
- 「連接相機 Wi-Fi」會顯示動作視窗：
  - `app 內連線相機`：用 `WifiNetworkSpecifier` 讓本 app 連到隱藏相機 Wi-Fi，目前不鎖 BSSID。
  - `複製密碼`
  - `新增隱藏 Wi-Fi`：Android 11+ 用 `Settings.ACTION_WIFI_ADD_NETWORKS`，Android 10 用 `WifiNetworkSuggestion`。
- app 內 Wi-Fi 連線成功後會自動測試 `http://192.168.42.1/`。
- HTTP 測試前會顯示目前 Wi-Fi SSID 與 IP，方便判斷是否真的切到相機網段。
- 提供 RTSP 預覽入口：`rtsp://192.168.42.1/live`。

## 目前已知限制

- 尚未完整重建舊 app 的 BLE handover protocol。
- 尚未實作拍照、錄影、媒體下載、縮圖、相機設定同步等完整功能。
- Android 10+ 不一定會把全手機 Wi-Fi 狀態切到相機 AP；`WifiNetworkSpecifier` 通常只把本 app 綁定到相機網路。
- 使用者實測目前仍可能無法連到隱藏 SSID，需繼續分析舊版 BLE handover 與相機 AP 啟動流程。
- `WIFI MAC` 目前不作為 BSSID 鎖定條件，因為實機測試時鎖 BSSID 可能讓 Android 直接回報找不到裝置。
- RTSP 目前只用 Android `VideoView` 測試，尚未導入舊版 `ijkplayer`。

## 舊版分析重點

- 舊版 app：`Mini Cam 1.2.1`
- 舊 package：`com.canon.cebm.minicam.android.us`
- 舊版可見 RTSP 端點：`rtsp://192.168.42.1/live`
- 舊版不要求使用者輸入 Wi-Fi 配對密碼。
- 舊版 `ConnectUtils` 可由序號推算 SSID 與密碼。
- 舊版中 `BLE MAC`、`WIFI MAC`、`serial` 是獨立欄位，未看到只從 serial 直接推導 MAC 的明確演算法。

## 網路安全

app 全域不開放 cleartext，只在 `network_security_config.xml` 允許：

```text
192.168.42.1
```

## 重要檔案

```text
app/src/main/kotlin/cam/sam/minicam/MainActivity.kt
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml
app/src/main/res/values/strings.xml
```

## 建置

```powershell
cd D:\github\oldApp\SamMiniCam
.\gradlew.bat :app:assembleDebug
```

Release：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\SamMiniCam
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
SamMiniCam/release/[版本]/release.apk
SamMiniCam/release/[版本]/release-notes.md
```
