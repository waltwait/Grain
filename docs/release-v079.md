# Grain 0.7.9

相機畫面的變焦倍率圓鈕由 48 dp 縮為 36 dp，倍率字由 14 sp 縮為 12 sp。外層仍為 48 dp 可點範圍，保留選取時的金色外框、方向旋轉、左右滑動變焦與錄影中變焦。按鈕位置和浮動變焦滑桿的錨點不變。

版本 0.7.9／versionCode 42，沿用原套件、正式簽章與十款富士 LUT。已於 2026-10-10 由 [GitHub Actions](https://github.com/waltwait/Grain/actions/runs/38056091852) 自動打包及發布為 [正式 Release](https://github.com/waltwait/Grain/releases/tag/v0.7.9)，只有一個 APK 附件。

2026-10-10 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 4 分 7 秒。257 項 JVM 測試通過，0 失敗／錯誤／跳過；lint 0 錯誤、30 警告。既有變焦及錄影裝置測試已編譯，目前 ADB 裝置清單為空，尚未在手機驗證觸控與畫面。

APK 版本、非 debuggable、原正式簽章與 16 KB ZIP 對齊通過。十款富士 LUT 校驗通過，41 個 LUT／字體／字體授權／native 檔案與 0.7.8 原始 bytes 相同。

原本的本機成品 `output/Grain-0.7.9-personal-fuji.apk`：15,038,022 bytes，SHA-256 `3df0a66b90238f10dd5549d6bcfeccffa86368396df38f6f2966a60c3a0acab4`；保留供本機建置紀錄查核，沒有拿它覆蓋 Actions 的附件。

正式發布使用 GitHub Ubuntu 24.04 建置的 [Grain-0.7.9-personal-fuji.apk](https://github.com/waltwait/Grain/releases/download/v0.7.9/Grain-0.7.9-personal-fuji.apk)：15,038,022 bytes，SHA-256 `84c2d23b5ed1381cb5aa493d5767d6d7228d99e6e54be070b13f03e1336367df`。正式工作耗時 2 分 58 秒，完成 JVM 測試、personal lint、裝置測試編譯、正式 APK、原簽章、十款富士 LUT 與 16 KB ZIP 對齊檢查。版本 tag 指向 `99a8194279622af5add25b476710bc4097ecdba6`；附件上傳並核對後，bot 提交 `308f70d` 更新 App／Pages 共用的 `updates/personal-fuji/latest.json`。不同建置成品的 APK SHA-256 不同，下載校驗以正式 feed 為準。

後續 Claude cloud 只負責開發與 PR；增加版本及發版說明後合併 main，由 Actions 完成正式打包、發布及更新資訊。環境的一次設定見 [雲端開發文件](cloud-development.md)。
