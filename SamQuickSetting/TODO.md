# SamQuickSetting TODO

## 多面板

目前已先完成資料層拆分，預設仍只有第一個面板：

- `grid`、`locked` 是所有面板共用設定。
- `active_panel`、`panel_count` 是全域面板狀態。
- 每個面板獨立保存 `items`、`background` 與按鈕位置。
- 舊版單面板資料會自動搬移到第 1 個面板。

舊版 `Quicker` 的多面板概念是 SQLite 三層資料：

```text
workspaces
screens
widgets
```

也就是一個 workspace 底下有多個 screen，每個 screen 底下有自己的 widgets。新版目前先用 `SharedPreferences` 模擬這個結構，之後如果需要大量面板、排序、備份匯入匯出，再考慮改 SQLite。

下一步：

1. 設定頁先做「切換面板 / 新增面板」。
2. 之後再決定要不要做左右滑動切換面板。
