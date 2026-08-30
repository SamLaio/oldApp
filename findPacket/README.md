# findPacket

`findPacket` 是由舊版「包裹到了沒」重建的新 Android 包裹查詢 app。

## 目前範圍

- 目前版本：`0.2`。
- 最低版本：Android 11（API 30）。
- 使用官方物流入口，不沿用舊版失效或明文 HTTP 網址。
- 輸入單號後會複製到剪貼簿；內建 WebView 會嘗試自動填入正確欄位，外部瀏覽器則只開啟官方頁。
- 可點擊下方最近查詢，將保存的物流商與單號帶回上方欄位。
- 可接收 Android 文字分享，會從分享文字自動抓取物流單號並填入欄位。
- 可確認的物流頁面已指定單號欄位；其他動態頁保留泛用偵測並避開驗證碼、站內搜尋與個資欄位。
- 主畫面內容已往下調整，並使用新版簡潔線條 app 圖示。
- 不自動爬物流狀態，不保存姓名、手機、身分證等取件資料。
- 不沿用舊 Firebase / AdMob 設定。

## 建置

```powershell
cd D:\github\oldApp\findPacket
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
findPacket/release/[版本]/release.apk
findPacket/release/[版本]/release-notes.md
```
