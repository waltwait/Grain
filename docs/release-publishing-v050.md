# Grain 0.5.0 Release 準備紀錄

使用者提出發布 Release 的需求。此輪完成正式簽章、R8 APK、AAB、校驗與私人 GitHub 草稿；發布目標與是否沿用使用者既有 keystore 仍待確認，尚未公開發布或上傳 Google Play。既有 `v0.5.0` 個人富士測試版保持原樣，正式成品另使用草稿標籤 `v0.5.0-release`，不移動原標籤或更改儲存庫可見性。

## 版本與內容

套件為 `tw.luma.camera`，versionName **0.5.0**、versionCode **22**。原有 Kotlin／Compose 功能沿用 0.5.0，Release 關閉 debuggable 並執行 R8 最佳化。正式成品含六款 Grain Originals 與五款 Pat David／Natron 的 Kodak 社群模擬，保留來源、轉換說明與 CC BY-SA 4.0 連結；APK／AAB 的 LUT 資產逐 byte 相同。官方富士 CUBE 只在 debug source set，本輪沒有將它們加入正式成品；LUT 匯入功能仍可使用，三個英文品牌類別保留。

相簿檢視頁移除放大／還原按鈕與上一張／下一張底列，照片使用左右滑動切換、雙指縮放與拖曳、雙擊還原比例。縮放時拖曳移動照片，還原後可繼續滑動換張；沒有新增畫面上的手勢說明。保留返回相簿、張數與影片播放控制，螢幕閱讀器仍可使用縮放／還原動作。

## 正式簽章

新增 `.signing/release.properties` 的本機設定支援，缺少設定時 Release 保持未簽章，不自動回退為 debug 簽章。設定存在但欄位不完整時，Gradle 會明確失敗，錯誤僅顯示缺少的欄位名稱。

準備成品使用新建立的 Grain 專用 RSA 4096／SHA256withRSA／PKCS12 key，alias `grain-release`、有效期 10000 天。certificate SHA-256 為 `e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8`，與原 debug certificate `322a4b82bbd7b7a23967f5b7703a05cb62f9cf62b6c63ea07b0799d24d99553d` 不同。首次由目前個人測試版轉入這份成品不能直接覆蓋，設定與已匯入 LUT 需先備份；本輪未進行手機安裝、移除或資料搬移。

`.signing` 目錄權限為 0700，keystore 與設定為 0600，均在 Git 忽略範圍。產生腳本使用隨機密碼，密碼透過環境變數交給 keytool，不印在終端或命令參數；已有檔案時拒絕覆蓋。密碼僅存在本機設定中，未提交 Git、未附加到 Release。需安全備份 `.signing/grain-release.jks` 與 `.signing/release.properties`，之後每次發布沿用同一把 key。正式密碼不應傳到聊天中。

簽章與更新識別依 [Android 的 App signing 文件](https://developer.android.com/studio/publish/app-signing)。若使用者已有 keystore，可在本機依 [範本](release-signing.properties.example) 填入檔案、格式、alias 與密碼後重建。Google Play 的上傳 key／App signing key 選擇需另依實際 Play Console 設定處理；簽好的 AAB 不代表已完成商店上架。

## 驗證

相簿修改後，`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest`、`assembleRelease` 與 `bundleRelease` 全部成功，耗時 **1 分 4 秒**。完整 **110 項 JVM 測試通過**，0 失敗／錯誤／跳過；Lint **0 錯誤、21 警告**。既有相簿裝置測試擴充為兩張測試照片，涵蓋雙指放大、雙擊還原與左右滑動切換；本次只完成編譯，沒有執行裝置測試。本專案未提供 `testReleaseUnitTest` 任務，先前嘗試於執行前失敗後改用現有任務，未把它記為通過。

APK 的 package/version metadata、非 debuggable、APK Signature Scheme v2、RSA 4096 及新 certificate 已確認。`zipalign -c -P 16 4` 通過；六個 arm64-v8a／x86_64 native libraries 的所有 ELF LOAD segments 至少 16 KB 對齊。這是靜態封裝檢查，沒有實際在 16 KB 裝置執行。

AAB 的 JAR 簽章驗證成功，專案內 bundletool 的 `validate` 也通過。JDK 17 jarsigner 另顯示自簽 certificate、無 timestamp、POSIX attributes 與 manifest 排列導致的 JarInputStream 提示；保留 AGP 原始輸出，未自行重排或重簽 bundle。這些結果不代替 Play Console 的接收與審查。

沒有連接手機或執行裝置測試。Release 的 R8 後相機、GPU、錄影、白平衡、持握方向、相簿與持續拍攝仍需實機驗證。

## 成品

| 成品 | Bytes | SHA-256 |
| --- | ---: | --- |
| `output/Grain-0.5.0-release.apk` | 11,667,522 | `bc5781c3da54bcc055696bec46057e0ba4cdd5287dd902574dbe636d5693e5a1` |
| `output/Grain-0.5.0-release.aab` | 13,081,045 | `2b83ff86bea2635159354897f6d2098853bd2f499eb7a9f192ded966c79a4f30` |

各有 `.sha256` 檔；R8 mapping 另存為 `output/Grain-0.5.0-release-mapping.txt`，不提交 Git。成品與簽章資訊均可重用，但不應重新產生 key 來取代已發布的 key。
