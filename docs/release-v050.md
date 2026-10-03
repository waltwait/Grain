# Grain 0.5.0 建置紀錄

依使用者要求將目前版本升為 **0.5.0**，versionCode 由 20 升為 **21**。拍攝、濾鏡、儲存及介面流程沿用 0.4.10，App 識別碼與原有簽章保持一致；此次沒有資料搬移或新增偏好格式。

此版包含 FUJIFILM／KODAK／GRAIN 三個英文濾鏡分類、每款強度記憶、照片與影片 LUT、錄影變焦、手動 ISO／快門與白平衡、內建相簿、成功不跳通知的拍照／存檔動畫，以及手機固定相機介面與獨立的成品方向。

## 驗證

Debug APK、AndroidTest APK、完整 JVM 測試、Lint 與 R8 Release 全部成功，耗時 **57 秒**。**110 項 JVM 測試通過**，0 失敗／錯誤／跳過；Lint **0 錯誤、21 警告**，與 0.4.10 相同。

沒有連接手機或操作 ADB，裝置測試只編譯，未執行。S24 的拍攝／影片方向、動畫體驗、手動 K 色溫與實際色彩仍沿用既有待驗證狀態，升版不代表完成實機驗證。

## 個人 APK

成品 `output/Grain-0.5.0-personal-fuji-debug.apk`，**53,302,803 bytes**（約 50.8 MiB），SHA-256 `6c57637c32dd0b7606d9837f5eb7fb72a06b0529eb4220211fe1f5fb8f0840f3`。套件為 `tw.luma.camera`，APK metadata 確認 versionName 0.5.0／versionCode 21。

簽章驗證通過，certificate SHA-256 `322a4b82bbd7b7a23967f5b7703a05cb62f9cf62b6c63ea07b0799d24d99553d` 與 0.4.10 相同，可直接更新。26 個 LUT 相關資產與 0.4.10 逐 byte 相同，個人 debug 保留十款官方富士 CUBE；Release 不含官方富士素材。

素材與個人 APK 保持 Git 忽略，只提供私人 prerelease。
