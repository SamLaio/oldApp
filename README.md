# oldApp

本 repository 用來保存舊 Android APK 解包資料，並重建成新的原生 Kotlin Android app。

## 專案結構

- `seClock/`：由舊版「時鐘小工具 白 / 黑」合併重建的新時鐘 Widget app。
- `findPacket/`：由舊版「包裹到了沒」重建的新包裹查詢 app。
- `fix/`：舊 APK 解包資料保存區，已設為 git ignore，不作為新版原始碼維護。
- `docs/`：評估紀錄與移植整理文件。

## App 慣例

- 新版 app 使用原生 Android Kotlin。
- 最低支援 Android 11（API 30）。
- 目前版本：`seClock 0.1`、`findPacket 0.2`。
- 每個 app 的 release 輸出放在各自 app 內：

```text
[app]/release/[版本]/release.apk
[app]/release/[版本]/release-notes.md
```

`release/`、APK、AAB、keystore 與本機簽章設定不進版本庫。

## 建置

Debug 建置：

```powershell
cd D:\github\oldApp\seClock
.\gradlew.bat :app:assembleDebug

cd D:\github\oldApp\findPacket
.\gradlew.bat :app:assembleDebug
```

Release 建置前先載入本機簽章環境：

```powershell
. D:\project\apkKey\oldApp.local.ps1
```

Release keystore 固定使用：

```text
D:\project\apkKey\oldApp.jks
```

## 授權

本專案使用 GPL-3.0 授權，完整條款見 [LICENSE](LICENSE)。
