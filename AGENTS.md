# AGENTS.md

這份文件提供 LLM 在本 repository 中工作的基本規則。本 repo 目前保存三個已解包的舊 Android APK app，後續目標是作為移植、重建與發版整理的基礎。

## 文件語言

- 本專案所有說明文件與慣例檔皆使用正體中文撰寫。
- 程式識別字、命令、路徑、錯誤訊息、上游連結、套件名稱、API 名稱與 Android component 名稱保留原文。
- 若修改使用者可見功能、設定、權限、建置方式、物流清單、Widget 行為或發版輸出，同步檢查相關文件是否需要更新。

## Git 操作授權

- 除非使用者在當前回合明確要求 `commit`、`push` 或 `release`，否則不要執行任何 git 動作；包含但不限於 `git add`、`git commit`、`git push`、`git revert`、建立 tag、修改 GitHub release 或上傳 release assets。
- 使用者先前回合曾要求過 `commit` / `push` / `release`，不代表後續回合仍授權。每次都必須以當前最新使用者訊息為準。
- 在未取得明確授權時，可以修改檔案、建置、測試與產出本機 artifact，但不可變更 git history、index、遠端 branch、tag 或 release。

## Commit 前檢查

每次 commit 前都要做：

1. 確認改動符合本 `AGENTS.md`。
2. 確認沒有誤改原始解包 APK 快照，除非使用者明確要求。
3. 若新增現代化重建專案，確認舊 APK 素材與新專案檔案分區清楚。
4. 若改到使用者可見功能、設定、權限、建置版本、物流清單、Widget 樣式或 release 產物，更新相關文件。
5. 執行可用的最小驗證；若無法執行，在回覆或 commit message 中說明原因。
6. 不要 stage 或 commit 使用者未要求處理的無關改動。
7. 不要提交 secret、keystore、簽章密碼、`local.properties`、`key.properties`、APK、AAB、ZIP、EXE 或 build cache。

## Push 前檢查

每次 push 前都要做：

1. 確認已完成 Commit 前檢查。
2. 執行本次改動需要的 Android / Kotlin / Java / Gradle 最小驗證流程。
3. 若本次 push 包含使用者可見變更，確認版本號是否需要先調整。
4. 若已產生 release notes，確認位於對應 app 資料夾內：

```text
seClock/release/[版本]/release-notes.md
findPacket/release/[版本]/release-notes.md
```

5. 若已建置 APK，確認 APK 固定放在對應 app 資料夾內：

```text
seClock/release/[版本]/release.apk
findPacket/release/[版本]/release.apk
```

6. 確認 release 產物版本、資料夾版本與 release notes 版本一致。
7. 不要 push APK、AAB、ZIP、EXE、keystore、`local.properties`、`key.properties`、build cache 或其他本機產物。

## Release 前檢查

每次 release 前都要做：

1. 確認對應 app 的 `release/[版本]/release-notes.md` 已存在且內容為本次版本。
2. 確認對應 app 的 `release/[版本]/release.apk` 已存在。
3. 確認 release APK 是目前程式最新版建置出來的檔案。
4. 確認 APK 版本與新專案的版本設定一致。
5. 確認 Android SDK、minSdk、targetSdk、簽章、權限與語系資訊符合本次 release。
6. 若 release 包含包裹查詢 app，確認各物流查詢入口已重新核對，並在 release notes 標明新增、移除或失效的物流項目。
7. 若 release 包含時鐘 Widget app，確認白/黑主題已二合一，並在支援的 Android Launcher 上測試 Widget 新增、更新與尺寸調整。
8. 發布 GitHub release 時，把對應 app 的 `release/[版本]/release-notes.md` 內容放到 release description。
9. 發布 GitHub release 時，把對應 app 的 `release/[版本]/release.apk` 與需要的其他產物一併附上去。

## 簽章設定慣例

- 本專案 Android release keystore 固定使用：

```text
D:\project\apkKey\oldApp.jks
```

- 本機簽章環境變數固定由下列檔案載入：

```powershell
. D:\project\apkKey\oldApp.local.ps1
```

- `oldApp.local.ps1` 只保存於本機，不進版本庫。
- Gradle 只從 `SIGNING_STORE_FILE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 讀取簽章資訊。
- 不要把 keystore、簽章密碼或本機簽章腳本複製到 app 資料夾或提交到 git。

## Release 輸出慣例

- 每個 app 的 release 輸出都放在 app 資料夾本身內，目前 app 資料夾為：

```text
seClock/
findPacket/
```

- APK 固定路徑：

```text
[app]/release/[版本]/release.apk
```

- release 說明固定路徑：

```text
[app]/release/[版本]/release-notes.md
```

- 若需要保存其他本機產物，放在同一版本資料夾下，並使用清楚檔名，例如：

```text
[app]/release/[版本]/source-audit.md
[app]/release/[版本]/carrier-check.md
```

- 各 app 底下的 `release/` 是本機發版輸出資料夾，不進版本庫。
