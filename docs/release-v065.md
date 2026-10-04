# Grain 0.6.5

照片檢視改為黑底滿版，底部用「照片／編輯」tab 切換；保留瀏覽位置、縮放、濾鏡與強度，編輯直接使用目前照片，儲存另存新照片且保留原圖。單點照片可隱藏／顯示工具列，沒有放大或前後張按鈕、手勢說明或成功儲存通知。影片播放／暫停改用圖示，影片檢視保留系統安全邊界。實作與裝置測試範圍見 [照片 tab 紀錄](gallery-photo-tabs.md)。

版本 0.6.5／versionCode 28，套件 `tw.luma.camera`，非 debuggable。使用 `personal` build type，沿用原正式簽章與十款富士 LUT。私人 Release 僅提供 `Grain-0.6.5-personal-fuji.apk` 一個附件；公開 Grain-pages 只同步網頁與 metadata，下載仍連到需登入的私人 Release。既有 0.6.4 與其他歷史版本保留。

2026-10-04 最後建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 2 分 19 秒。143 項 JVM 測試通過，0 失敗／錯誤／跳過；lintPersonal 0 錯誤、22 警告。裝置測試完成編譯，實際觸控、GPU 預覽與覆蓋安裝尚未在手機驗證。十款富士原始 bytes、共 38 個 LUT 相關／native 檔案與已發布 0.6.4 完全一致；原正式簽章與 16 KB ZIP 對齊檢查通過。

本機成品 `output/Grain-0.6.5-personal-fuji.apk`：14,016,730 bytes，SHA-256 `e37502e2861ba11251e1adf7bd134718f7be5700848c8629b71b41ab7151e956`。版本 metadata 與下載頁均從實際成品驗證產生。

2026-10-04 已推送 main 與 v0.6.5，標籤對應程式碼提交 `fffcc0e1602e878da2a8806a23f92aff86739fb2`。已發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.5)，GitHub 回報最新版為 v0.6.5、非草稿、非預發布，只有一個 APK；遠端大小 14,016,730 bytes 與 SHA-256 完全符合本機成品。發布後再次確認 App 儲存庫 private=true、has_pages=false。

公開 Grain-pages 的 metadata 提交為 `a328771e0444ca2e65794aab1886a8cd0724f774`，[部署 37179662299](https://github.com/waltwait/Grain-pages/actions/runs/37179662299) 成功。匿名讀取線上 `latest.json` 與本機驗證後的公開 metadata 完全相同，版本 0.6.5、十款富士、access=github-login；網站按鈕沿用私人 `/releases/latest`。公開儲存庫仍僅含五個網頁／metadata 檔案及 README，沒有 APK、LUT、簽章或登入憑證。
