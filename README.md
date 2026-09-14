# oldApp

本 repository 保存舊 Android APK 解包資料，並把其中可維護的功能重建成原生 Kotlin Android app。

## 目前 app

| 資料夾 | app | package | 版本 | 最低 Android | 現況 |
| --- | --- | --- | --- | --- | --- |
| `seClock/` | seClock | `com.samliao.seclock` | `0.1` | Android 11 | 時鐘 Widget app，已可建立 4 種桌面透明時鐘 widget |
| `findPacket/` | findPacket | `com.samliao.findpacket` | `0.5` | Android 10 | 包裹查詢 app，使用官方物流頁、內建 WebView、自動填單號、查詢歷史 |
| `SamMiniCam/` | SamMiniCam | `cam.sam.minicam` | `0.1` | Android 10 | Canon Mini Cam / FV-100 硬體連線測試 app，尚未完整移植拍照與媒體管理 |
| `SamQuickSetting/` | SamQuickSetting | `tw.idv.samliao.quick.setting` | `0.3` | Android 10 | 懸浮快速設定面板 app，可放設定鈕、滑桿、app 捷徑與側邊條 |

## 其他資料夾

- `fix/`：舊 APK 解包資料保存區，已 gitignore，不作為新版原始碼維護。
- `docs/`：移植評估與整理文件。
- `graft/`：本機程式結構索引快取，已 gitignore。
- `.tmp_*`：本機臨時資料夾。

## Release 慣例

每個 app 的本機 release 輸出放在 app 自己的資料夾：

```text
[app]/release/[版本]/release.apk
[app]/release/[版本]/release-notes.md
```

`release/`、APK、AAB、keystore、簽章腳本與 build cache 不進版本庫。

## 建置

Debug 建置：

```powershell
cd D:\github\oldApp\[app]
.\gradlew.bat :app:assembleDebug
```

Release 建置前先載入本機簽章環境：

```powershell
. D:\project\apkKey\oldApp.local.ps1
cd D:\github\oldApp\[app]
.\gradlew.bat :app:assembleRelease
```

Release keystore 固定使用：

```text
D:\project\apkKey\oldApp.jks
```

## 文件

各 app 的實作現況請看：

```text
seClock/README.md
findPacket/README.md
SamMiniCam/README.md
SamQuickSetting/README.md
```

`seClock/HANDOFF.md` 是分出去到另一個對話時使用的交接筆記。

## 授權

本專案使用 GPL-3.0 授權，完整條款見 [LICENSE](LICENSE)。
