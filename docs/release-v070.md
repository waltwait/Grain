# Grain 0.7.0

2026-10-04 使用者將 `waltwait/Grain` 改為 public，並要求把 App 內建檢查更新做起來。0.7.0／versionCode 33 恢復 0.6.2 的原生更新流程：設定 → 檢查更新 → 下載新版 → 安裝。沿用個人富士包、套件 `tw.luma.camera` 與原正式簽章，沒有改動相機、相簿或編輯功能。

## 更新來源

`app/build.gradle.kts` 依 build type 預設帶入公開版本資訊網址，不再預設留空：

| build type | 通道 | 預設網址 |
| --- | --- | --- |
| debug、personal | `personal-fuji` | `https://raw.githubusercontent.com/waltwait/Grain/main/updates/personal-fuji/latest.json` |
| release | `release` | `https://raw.githubusercontent.com/waltwait/Grain/main/updates/release/latest.json` |

`-PgrainUpdateUrl=https://…` 可改用自架來源，`-PgrainUpdateUrl=`（空值）會關閉原生更新並回到開啟 GitHub 最新 Release 的瀏覽器流程；`http://` 與帶帳密的網址仍會讓建置失敗。三個 build type 的 BuildConfig 都已實際產生並核對，APK dex 內也確認含個人通道網址。

更新頁進入時自動檢查一次。下載沿用既有保護：只走 HTTPS、最多跟隨 6 次重新導向、限制資訊檔 256 KB 與 APK 100 MB，核對大小、SHA-256、套件名、versionCode 與簽章，只接受較高版號；個人通道必須含十款富士 LUT，不能被一般包取代。安裝仍開啟 Android 系統安裝畫面，需要使用者允許 Grain 安裝未知來源 App，沒有靜默更新，也沒有開 App 時的背景檢查。

## 首次安裝與資料

0.6.9 以前的 APK 沒有更新網址，點「檢查更新」只會開瀏覽器，所以 0.7.0 要手動下載後覆蓋安裝一次，之後才能在 App 內更新。簽章與 0.6.x 相同，覆蓋安裝保留設定與已匯入 LUT。

## 發布順序

`updates/<channel>/latest.json` 由 `raw.githubusercontent.com` 提供，回應 `cache-control: max-age=300`，新版最多約五分鐘後才被 App 看到。App 會依這份檔案下載附件，所以順序不能反：

1. 建置並驗證 APK，存 `output/Grain-<版本>-personal-fuji.apk`、`.sha256` 與 R8 mapping（均被 Git 忽略）。
2. 提交程式與文件，推送 main 與版本標籤。
3. 建立 GitHub Release 並上傳 APK，核對附件大小與 SHA-256；一個 Release 只放一個 APK。
4. 附件確認後，用 `scripts/prepare_update_site.py` 以該 APK 產生 `latest.json`（`--apk-url` 填實際附件網址、`--notes-file` 填更新說明），複製到 `updates/<channel>/latest.json`，提交並推送。
5. 匿名讀取 raw JSON，確認與本機產生的檔案完全相同；之後再用較低版號的 App 實際檢查更新。

通道不能混用：個人富士包只更新 `personal-fuji`，一般 release 只更新 `release`。不要刪除或改寫舊版本 APK 網址，也不要讓高版號 `latest.json` 指向不存在或不同的 APK。

## 公開狀態

2026-10-04 起 `waltwait/Grain` 為 public，既有所有 Release 附件可匿名下載，包含 v0.4.1 到 v0.5.0 的 debug 簽章個人富士預發布與 v0.6.1 到 v0.6.9 的個人富士包，內含十款官方富士 LUT；使用者在知道此情況後選擇不更動任何 Release。儲存庫沒有 LICENSE 檔，commit 作者信箱公開。公開前核對 Git 追蹤檔與歷史：沒有 keystore、私鑰、權杖或富士 `.cube` 原檔，這是具體項目檢查，不是完整安全審計。

`updates/release/latest.json` 仍是 0.6.0 的一般版資訊，一般通道在發布新的一般版前不能視為最新。`Grain-pages` 下載頁仍顯示「登入 GitHub 下載」，等待後續更新文字。

## 驗證

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 3 分 51 秒。153 項 JVM 測試通過，0 失敗／錯誤／跳過；lint 0 錯誤、30 項警告。更新模組另有 17 項測試（版本、metadata、通道、簽章、下載與取消）通過。裝置測試 APK 已編譯，沒有手機連線，更新頁、授權、下載、安裝與覆蓋更新都沒有在實機執行。

APK 確認 versionCode 33、versionName 0.7.0、非 debuggable、最低 SDK 29、正式簽章憑證 `e6c756c9…c1217c8`（v2 簽章）與 16 KB ZIP 對齊通過。十款富士 LUT 原始 bytes 通過校驗，38 個 LUT／native 檔案與 0.6.9 逐項相同。

成品 `output/Grain-0.7.0-personal-fuji.apk`：14,049,498 bytes，SHA-256 `bf58af7e05c8184d6bbb8b03091736ab59b2f4024936000f8b64c0b383910662`。

匿名完整下載尚未驗證：在開發機上從 GitHub 下載 0.6.9 APK，120 秒只收到約 7 MB 就逾時，網路速度偏慢；App 的逾時是每次讀取 30 秒，不是整體時限，慢但持續的下載應可完成，仍待實機確認。
