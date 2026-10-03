# Grain 0.6.1 個人富士濾鏡恢復

使用者更新 0.6.0 後回報富士濾鏡消失。比對成品確認：`Grain-0.6.0-release.apk` 中的富士 CUBE 為 **0 個**，原 `Grain-0.5.0-personal-fuji-debug.apk` 為 **10 個**。載入器依 `assets/luts/fujifilm` 的素材建立選單，沒有以 `BuildConfig.DEBUG` 限制；根因是正式 build type 未打包 debug source set 中的個人素材。

## 修復

新增 `personal` build type，複製既有 `release` 設定，沿用正式簽章、R8 與非 debuggable 設定，再加入本機 `src/debug/assets`。不複製 debug Kotlin／UI tooling，不將素材移到共同 `main` source set。套件仍為 `tw.luma.camera`，版本 **0.6.1／versionCode 24**，可更新 0.6.0 正式簽章包並保留目前資料；原 debug 簽章系列仍不同。

恢復 CLASSIC CHROME、CLASSIC Neg.、REALA ACE、PROVIA、Velvia、ASTIA、PRO Neg. Std、ETERNA、ETERNA Bleach Bypass、ACROS。相機與照片編輯共用原有 LUT 列表，保留新照片編輯頁、左右滑動變焦、透明控制與相簿手勢。

`verifyPersonalFujiAssets` 在 `prePersonalBuild` 前檢查十款 CUBE、來源 metadata 與正式簽章設定；缺少時直接失敗。`scripts/verify_personal_fuji_apk.py` 檢查成品十款名稱、已查核來源 ZIP 指紋、每個 CUBE 的 SHA-256 與 33-grid 標記。素材、APK 與簽章均保持 Git 忽略。

建置方式依 [Android build variants 文件](https://developer.android.com/build/build-variants)。素材來源與個人使用範圍沿用 [原富士素材紀錄](personal-fuji-build.md)；此包提供於私人儲存庫，公開下載方案仍另待確認，不能將個人素材包自動搬到公開下載區。

## 驗證

先以 0.6.0 APK 執行校驗腳本，正確失敗並列出十款缺檔；同一腳本驗證舊個人 APK 通過。修復後的新個人 APK 通過，十款官方檔案均與既有來源 metadata 的 SHA-256 一致。

`testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal`、`assembleRelease` 全部成功，耗時 **1 分 59 秒**。**126 項 JVM 測試通過**，0 失敗／錯誤／跳過；personal lint **0 錯誤、20 警告**。建置前素材 guard 確認執行。裝置測試完成編譯，未執行手機測試。

APK metadata 確認 `tw.luma.camera`／0.6.1／24，非 debuggable；APK Signature Scheme v2 與 16 KB ZIP 對齊通過。certificate SHA-256 為 `e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8`，與 0.6.0 正式包相同。其餘 11 個 Grain／Kodak LUT 未變，12 個 native libraries 與已驗證的 0.6.0 相同；同輪一般 release 仍未包含富士素材。

## 成品

`output/Grain-0.6.1-personal-fuji.apk`，**13,966,866 bytes**，SHA-256 `9071035eec1a7fc218e31015fcb72b0bc21f92fd5943897a7aa01b6edd7c3457`。checksum 與 R8 mapping 保留本機，私人 Release 只附一個 APK；既有 0.6.0 發布成品保留。
