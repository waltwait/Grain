# Grain 0.7.7

App 圖示換成下載主頁上的標誌：圓角機身、鏡頭圈和右上角一顆金點。版本 0.7.7／versionCode 40，沿用正式簽章、套件與十款富士 LUT。沒有功能變更。

## 變更

- **新圖示：** 前景是向量圖（機身 `#6C634C`、鏡頭圈 `#B9A66E`、金點 `#DBBD6E`），背景 `#11110F`；Android 13 以上的主題圖示（單色）用同樣的形狀，機身比鏡頭淡一點。形狀與位置取自 `website/style.css` 的 `.frame`，機身寬 56 dp 置中，最遠處離中心 32.2 dp，在圓形遮罩的 33 dp 安全區內。
- **APK 變小：** 舊圖示是一張 1.5 MB 的點陣圖，現在不再使用並已刪除，APK 由 16,452,786 縮為 15,038,022 bytes（約 −1.4 MB）。
- **網站圖示同步：** 下載主頁的 favicon、apple-touch 圖示與連結預覽圖改成同一個標誌。
- **產生方式：** `scripts/render_icon.py` 由同一組形狀產生 Android 向量、網站圖檔與預覽圖（需要 Pillow）；產生時會檢查圖形沒有超出安全區。

## 驗證

2026-10-05 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 4 分 17 秒。257 項 JVM 測試通過，0 失敗／錯誤／跳過；lint 0 錯誤、25 項警告（與 0.7.6 相同）。APK 確認 versionCode 40、versionName 0.7.7、非 debuggable，啟動圖示解析為 adaptive icon，正式簽章憑證 SHA-256 `e6c756c9…c1217c8`（v2 簽章），16 KB ZIP 對齊與十款富士 LUT 原始 bytes 通過校驗，APK 內沒有大於 200 KB 的 PNG。

成品 `output/Grain-0.7.7-personal-fuji.apk`：15,038,022 bytes，SHA-256 `8a9ba0eb6e528540cffea4ec56cb743abab0a727f77ff3514734e42ff8ce1423`。2026-10-05 已推送 main 與 v0.7.7，標籤對應 `9286681a3095a9443fa2024bb06d338661d94c6e`。已發布 [公開 Release](https://github.com/waltwait/Grain/releases/tag/v0.7.7)，Release ID 403824410；草稿 APK 的大小與 SHA-256 核對後才公開。GitHub 最新版為 v0.7.7、非草稿、非預發布，只有一個 `Grain-0.7.7-personal-fuji.apk`，匿名下載連結回 200。`updates/personal-fuji/latest.json`（提交 `6fcd40a`）在附件確認後、Release 公開約 5 秒後更新為 0.7.7／40；repo 內的檔案與本機產生的完全相同。

沒有手機連線：**圖示在實機桌面（各家廠商的遮罩形狀、主題圖示的著色）上的樣子沒有看過**，只有用圓形與圓角方形遮罩算出來的預覽。
