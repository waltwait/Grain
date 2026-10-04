# Grain 0.6.9

相簿主頁移除右上角重新整理按鈕。沿用 Activity 回到前景與照片另存後的自動更新，讀取失敗時仍保留頁內重試入口。編輯頁的右上儲存勾號保留。

截圖中 Grain 標題與返回圖示過暗，是主題沒有提供根層內容色，導致未指定顏色的文字和圖示繼承黑色。MainActivity 在深色 MaterialTheme 內補上 Surface 與 onBackground，讓主頁與編輯頁的預設內容在黑底清楚可見。Grain 標題另外指定暖白 #F5F3EB、22sp 粗體，張數／批次預覽位置使用 labelMedium 中字重及 78% 白色。

版本 0.6.9／versionCode 32，沿用正式簽章、套件與十款富士 LUT。字體比較屬設計建議，本版沒有增加字體檔；介面目前沿用系統字體。既有相簿裝置測試補上 Grain 標題顯示與重新整理按鈕不存在的檢查。

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 5 分 40 秒。153 項 JVM 測試通過，0 失敗／錯誤／跳過；lint 0 錯誤、30 項既有警告。裝置測試 APK 已編譯，實機操作與覆蓋安裝尚未驗證。

APK DEX 已確認 `gallery-title` 與既有主頁 tab／批次編輯 UI 標記，重新整理相簿的描述已移除。十款富士 LUT 原始 bytes、共 38 個 LUT／native 檔案與 0.6.8 完全相同；原正式簽章與 16 KB ZIP 對齊通過。

成品 `output/Grain-0.6.9-personal-fuji.apk`：14,049,494 bytes，SHA-256 `b49b2b82a9509fc068af40a1dbaa7acfddeca535dcc3640234ee4069ac2c802e`。

2026-10-04 已推送 main 與 v0.6.9，標籤對應 `a2b12052e8d52fd8ba506b496b2129586971e3aa`。已發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.9)，Release ID 402971970；草稿 APK 的大小與 SHA-256 核對後才發布。GitHub 最新版為 v0.6.9、非草稿、非預發布，只有一個 `Grain-0.6.9-personal-fuji.apk`；發布後附件大小、SHA-256 與標準下載網址和本機 metadata 相同。App 維持 private=true、has_pages=false。

公開下載頁 metadata 提交 `1ad1d9eb24203c09e68a2f9872800e25d73bbacb`，[部署 37196694850](https://github.com/waltwait/Grain-pages/actions/runs/37196694850) 成功。匿名線上 `latest.json` 與本機公開 metadata 完全相同，版本 0.6.9／32、access=github-login。公開儲存庫仍只有六個既有網頁／說明／metadata 檔案，沒有 APK 或 LUT。

字體建議另提供離線 HTML 與實際字型對照圖，存於被 Git 忽略的 `output/font-preview/`，含字體來源與 OFL 授權。推薦 Newsreader 品牌標題搭配 Noto Sans TC 操作文字；另比較 DM Sans 與 Noto Serif TC。此設計預覽未加入 APK 或公開下載頁。
