# Grain 0.7.9

相機畫面的變焦倍率圓鈕由 48 dp 縮為 36 dp，倍率字由 14 sp 縮為 12 sp。外層仍為 48 dp 可點範圍，保留選取時的金色外框、方向旋轉、左右滑動變焦與錄影中變焦。按鈕位置和浮動變焦滑桿的錨點不變。

版本 0.7.9／versionCode 42，沿用原套件、正式簽章與十款富士 LUT。本次僅準備本機更新 APK，尚未發布 Release 或改動公開更新資訊。

2026-10-10 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 4 分 7 秒。257 項 JVM 測試通過，0 失敗／錯誤／跳過；lint 0 錯誤、30 警告。既有變焦及錄影裝置測試已編譯，目前 ADB 裝置清單為空，尚未在手機驗證觸控與畫面。

APK 版本、非 debuggable、原正式簽章與 16 KB ZIP 對齊通過。十款富士 LUT 校驗通過，41 個 LUT／字體／字體授權／native 檔案與 0.7.8 原始 bytes 相同。

成品 `output/Grain-0.7.9-personal-fuji.apk`：15,038,022 bytes，SHA-256 `3df0a66b90238f10dd5549d6bcfeccffa86368396df38f6f2966a60c3a0acab4`。
