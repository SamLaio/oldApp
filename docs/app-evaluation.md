# 舊 App 移植評估

核對日期：2026-08-30

本 repo 目前保存三個已解包 APK：

- `包裹到了沒 1.1.11`
- `時鐘小工具 白 1.1`
- `時鐘小工具 黑 1.1`

本評估只處理移植與重建方向，不修改原始解包 APK 快照。

目前已建立兩個新版專案：

- `seClock/`：原生 Android Kotlin 時鐘 Widget app。
- `findPacket/`：原生 Android Kotlin 包裹查詢 app。
- 兩個新版專案目前版本皆為 `0.1`。

## 結論

時鐘 app 適合重寫成一個新的 Android Widget app，白色與黑色主題二合一。不要直接重包舊 APK：舊版依賴 Sony runtime skinning 與 Xperia widget category metadata，targetSdk 只有 25，且權限過舊。

包裹 app 適合重寫成一個「物流入口 + 查詢紀錄 + WebView/外部瀏覽器」app。不要嘗試維護舊 APK 本體：舊版 targetSdk 30、含舊 Firebase / AdMob、允許 cleartext HTTP，且多個物流網址已改版、轉址或失效。

## 時鐘 App 評估

### 舊版現況

兩個時鐘 app 幾乎是同一份程式：

- `classes.dex` 完全相同。
- 所有 `res/layout`、`res/xml`、PNG 資源完全相同。
- 差異只在 `AndroidManifest.xml`、`resources.arsc` 與簽章檔。
- 白版 package：`com.sonymobile.advancedwidget.Lightclock.cz`
- 黑版 package：`com.sonymobile.advancedwidget.Darkclock.cz`
- 版本：`1.1`
- versionCode：`190726`
- minSdk：23
- targetSdk：25

舊版提供的 widget 型態：

- 類比時鐘：`HSAnalogClock`
- 一般數位時鐘：`HSWhiteDigitalClockPlain`
- 細字數位時鐘：`HSWhiteDigitalClockThin`
- 直式數位時鐘：`HSWhiteVerticalDigitalClock`
- Sony Style Cover 用數位時鐘：`SCWhiteDigitalClockPlain`

### 移植建議

新 app 建議只做一個 package，例如：

```text
com.samliao.seclock
```

第一版保留：

- `Sony時鐘` 3×2 widget
- `Sony時鐘2` 3×2 widget
- `數位時鐘` 3×1 widget
- `指針時鐘` 2×2 widget
- 主題選項：自動 / 白字版本 / 黑字版本
- 透明底 Widget
- 舊版 widget 選擇器預覽圖
- 12/24 小時依系統設定
- 日期、星期與下一個鬧鐘顯示

第一版移除或延後：

- Sony runtime skinning
- Sony Style Cover 專用 metadata
- `READ_PHONE_STATE`
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`
- 任何 launcher/vendor 專屬 category

### 技術路線

最省力版本：

1. 新建標準 Android 專案。
2. 使用 Kotlin + `AppWidgetProvider` + `RemoteViews`。
3. 使用一個設定 Activity 控制主題。
4. 每個 widget provider 對應一種版型。
5. 圖檔可從舊版 `res/drawable-*` 取用，layout 則照外觀重新寫。

目前新版專案位於 `seClock/`，使用原生 Android Kotlin、`AppWidgetProvider` 與 `RemoteViews`，最低版本為 Android 11（API 30）。

暫不建議第一版使用 Jetpack Glance，因為舊版是傳統時鐘 widget，`RemoteViews` 已足夠，依賴更少。

### 風險

- Android 新版對背景更新與精準時間刷新有限制；數位時鐘應優先使用 `TextClock`，不要自己排程每分鐘更新。
- 類比時鐘若沿用 `AnalogClock`，要確認 targetSdk 升級後實機顯示正常。
- 原 Sony Style Cover 功能可能無法在非 Sony 裝置重現，建議不列為第一版需求。

## 包裹 App 評估

### 舊版現況

舊版 package：

```text
obf.studio.packagequery
```

版本：

```text
1.1.11 / versionCode 20
```

主要 component：

- `MainActivity`
- `WebActivity`
- `PackageQueryApplication`
- Google Ads / Firebase measurement component

舊版功能推定：

- 輸入單號
- 選擇物流公司
- 輸入備註
- 保存最近查詢紀錄
- 開啟內建 WebView 查詢頁
- 複製 / 貼上單號
- 用瀏覽器開啟

舊版問題：

- 多數查詢入口是硬編碼網址。
- 許多網址是 `http://`，舊 app 也設定 `usesCleartextTraffic="true"`。
- 內建 Firebase / AdMob 版本很舊。
- 查詢邏輯與資源已混淆，直接維護成本高。
- 多個物流頁面需要驗證碼或 JavaScript，不適合承諾背景自動抓狀態。

目前新版專案位於 `findPacket/`，package 為 `com.samliao.findpacket`，使用原生 Android Kotlin，最低版本為 Android 11（API 30），預設不允許明文 HTTP。

### 物流入口核對

狀態定義：

- 保留：官方入口仍存在，第一版可放入。
- 更新：舊網址已轉址或過舊，應改用新入口。
- 降級：只能開官方網頁，不建議自動解析。
- 移除候選：舊入口失效、公司服務不明，除非使用者仍需要。

| 舊項目 | 建議狀態 | 新入口 / 備註 |
|---|---|---|
| 黑貓宅急便 | 更新 | `https://www.t-cat.com.tw/inquire/trace.aspx` |
| 中華郵政 | 保留 | `https://postserv.post.gov.tw/pstmail/main_mail.html` |
| 7-11 交貨便 / 取貨便 | 更新 | `https://eservice.7-11.com.tw/e-tracking/search.aspx`，另可保留 `https://myship.7-11.com.tw/` |
| 全家 FamiPort 店到店 | 更新 | `https://fmec.famiport.com.tw/FP_Entrance/QueryBox` |
| OK 超商 | 保留、降級 | `https://ecservice.okmart.com.tw/Tracking/Search`，需要驗證碼 |
| 萊爾富 | 降級 | 官網存在，但公開貨態入口不如 OK / 全家明確；可先透過 ezShip 或官方頁開啟 |
| EZShip | 更新 | `https://www.ezship.com.tw/receiver_query/ezship_query_shipstatus_2017.jsp` |
| 蝦皮店到店 | 新增/保留 | `https://spx.tw/` |
| 台灣宅配通 / 白鳥 | 更新 | `https://query2.e-can.com.tw/ECAN_APP/search.shtm` 或 `https://www.e-can.com.tw/search_goods.aspx` |
| 新竹物流 | 更新 | `https://www.hct.com.tw/Search/SearchGoods_n.aspx` |
| 嘉里大榮 | 更新 | `https://www.express.com.tw/tools/positchecking.aspx` |
| 便利帶 | 更新 | `https://www.25431010.tw/tracking` |
| 超峰快遞 | 更新候選 | 舊 app 名稱仍可對應到超峰速件，入口需再用實際單號確認 |
| 順豐速運 | 更新 | `https://htm.sf-express.com/tw/tc/dynamic_function/waybill/` |
| FedEx | 更新 | `https://www.fedex.com/zh-tw/tracking.html` |
| DHL | 更新 | `https://www.dhl.com/tw-zh/home/tracking.html` |
| UPS | 更新 | `https://www.ups.com/track?loc=zh_TW` |
| TNT | 更新 | `https://www.tnt.com/express/zh_tw/site/shipping-tools/tracking.html` |
| DPEX | 更新 | `https://dpex.com/track-and-trace/` |
| 4PX 遞四方 | 更新 | `https://track.4px.com/` |
| EMS 中國郵政 | 降級 | 官方站仍存在，但舊查詢頁有 SSL / JS 相容問題；第一版只開官方頁 |
| 圓通 YTO | 降級 | 官方站存在，但查詢入口與地區版本需再確認 |
| 百世快遞 | 移除候選 | 舊 `800bestex.com` DNS 查詢失敗；可改用 `800best.com`，但台灣使用價值需確認 |
| 捷利 / 增利 | 移除候選 | 公司站仍存在，但舊淘寶集運情境偏歷史需求 |
| 達康物流 / 露天拍賣 | 移除候選 | 露天相關超商配送規則已改，舊入口不宜保留為主功能 |
| 好運袋 | 移除候選 | 公司登記存在，但公開查詢入口不明 |

### 移植建議

第一版只做 WebView 聚合器，不做自動爬貨態：

1. 物流公司清單以本機 JSON 維護。
2. 每筆物流包含名稱、分類、查詢 URL、是否支援自動帶入單號、備註。
3. App 保存最近查詢：單號、物流、備註、查詢時間。
4. 點查詢時開內建 WebView；若網站阻擋或需要外部瀏覽器，提供外部開啟。
5. 對有簡單 query parameter 的物流再做自動帶入；需要驗證碼的不要解析結果。

第二版再考慮：

- 條碼掃描。
- 多筆查詢。
- 常用物流排序。
- 通知追蹤。
- 第三方追蹤 API。

第三方追蹤 API 不是第一版必要項目，因為成本、隱私、配額與準確率都需要另外評估。

### 安全與合規

- 新版不要沿用舊 Firebase project / AdMob id，除非使用者確認仍由自己持有。
- 預設不允許明文 HTTP；只對不可避免的舊站加 domain-level network security exception。
- 不保存身分證、姓名、手機等取件資料；若站台需要，交給官方網頁輸入。
- 不把單號送到非官方第三方查詢站，除非使用者明確同意。

## 建議開工順序

1. 先建立新 Android workspace。
2. 新增 `legacy/` 或保留現有三個解包資料夾作為只讀參考。
3. 先做時鐘二合一，因為範圍小、相依少、可快速產出 APK。
4. 再做包裹查詢第一版 WebView 聚合器。
5. 發版前補 `[app]/release/[版本]/carrier-check.md`，記錄每個物流入口的核對日期與狀態。

## 不建議事項

- 不建議直接反編譯後修舊 APK。
- 不建議把兩個時鐘 APK 用 manifest 合併後重簽。
- 不建議第一版承諾自動解析所有物流狀態。
- 不建議提交 release APK、AAB、ZIP、keystore 或本機 build cache。
