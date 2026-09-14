# findPacket

`findPacket` 是由舊版「包裹到了沒」重建的新 Android 包裹查詢 app。

## 現況

- app 名稱：`findPacket`
- package / applicationId：`com.samliao.findpacket`
- 版本：`0.5`
- `versionCode`：`5`
- 最低版本：Android 10，`minSdk 29`
- `targetSdk` / `compileSdk`：`35`
- 技術：原生 Android Kotlin，無第三方 UI 框架。

## 已實作功能

- 輸入物流單號、備註與物流商。
- 開啟內建 WebView 查詢頁。
- 開啟外部瀏覽器查詢頁。
- 查詢時會把單號複製到剪貼簿。
- 內建 WebView 會嘗試自動填入單號：
  - 物流商有指定 selector 時先用指定欄位。
  - 其他頁面使用泛用 scoring，避開驗證碼、搜尋、姓名、手機、密碼、email 等欄位。
  - 自動填入會在頁面完成後立即跑，並延遲重試 0.8 秒與 2 秒。
- 可接收 Android 文字分享：
  - `ACTION_SEND`
  - `text/plain`
  - 自動從文字抽出疑似物流單號。
  - 使用 `singleTask` 重用既有 app 視窗。
- 查詢歷史使用 SQLite 保存一年內資料。
- 點擊歷史會把物流商、單號、備註帶回上方欄位。
- 長按歷史可多選，並用刪除 / 取消按鈕管理。
- 左下角日月圓鈕可切換日間 / 夜間模式。

## 目前物流清單

```text
黑貓宅急便
中華郵政
7-11 交貨便 / 取貨便
全家 FamiPort 店到店
萊爾富
OK 超商
ezShip
蝦皮店到店
台灣宅配通
新竹物流
嘉里大榮
便利帶
順豐速運
FedEx
DHL
UPS
TNT
DPEX
4PX 遞四方
EMS 中國郵政
圓通 YTO
```

## 重要檔案

```text
app/src/main/kotlin/com/samliao/findpacket/MainActivity.kt
app/src/main/kotlin/com/samliao/findpacket/WebActivity.kt
app/src/main/kotlin/com/samliao/findpacket/Carriers.kt
app/src/main/kotlin/com/samliao/findpacket/Carrier.kt
app/src/main/AndroidManifest.xml
```

## 權限與限制

- 目前只宣告 `INTERNET`。
- 不自動抓物流狀態。
- 不保存姓名、手機、身分證或取件資料。
- 不沿用舊 Firebase / AdMob 設定。
- 物流網站若改版或加入驗證碼，內建自動填入可能失效，仍可用外部瀏覽器或剪貼簿手動貼上。

## 建置

```powershell
cd D:\github\oldApp\findPacket
.\gradlew.bat :app:assembleDebug
```

Release：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\findPacket
.\gradlew.bat :app:assembleRelease
```

## Release 輸出

```text
findPacket/release/[版本]/release.apk
findPacket/release/[版本]/release-notes.md
findPacket/release/[版本]/carrier-check.md
```
