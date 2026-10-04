# Grain 0.7.1

編輯匯出的照片改存到獨立資料夾，並成對保存原圖，相簿「編輯」tab 顯示編輯成品，可切換看原圖。版本 0.7.1／versionCode 33 升為 34，沿用正式簽章、套件與十款富士 LUT。設計見 [規格](superpowers/specs/2026-10-04-separate-edit-storage-design.md)，實作步驟見 [計劃](superpowers/plans/2026-10-04-separate-edit-storage.md)。

## 變更

- **編輯成品另存：** 編輯匯出存到 `Pictures/Grain Edits/`，每次成對寫入 `GRAIN_EDIT_<時間>.jpg`（改完圖）與 `GRAIN_EDIT_<時間>_original.jpg`（來源檔的 byte 複製），兩個都成功才公開，批次編輯每張各成一對。原圖副本保留來源的實際格式：PNG、HEIC、WebP 等來源會用自己的副檔名與 MIME 類型保存，不會被標成 JPEG。拍照仍存 `Pictures/Grain/`，「同時儲存原圖」開關行為不變。配對只靠同一組檔名，沒有資料庫。既有照片不搬動。
- **編輯 tab 首頁：** 尚未選照片時，上方是「選擇照片」按鈕，下方是「編輯成品」格子牆，只列改完圖；沒有成品時只有按鈕。「照片」tab 不再含編輯成品。
- **檢視頁切換：** 點成品開啟檢視頁，頂部右側有「看原圖／看改完」；找不到同組原圖（被刪除或檔名因重名被系統改過）就不顯示切換。切換狀態記在「哪一張照片」上，不是記在頁面上，翻頁後的下一張一定從改完圖開始，翻回來也回到改完圖，旋轉螢幕不會丟失目前的切換。
- **相機縮圖：** 編輯存完不再取代相機左下角縮圖，只重新整理相簿。設定頁路徑說明加入「編輯：Pictures/Grain Edits」。
- **存完離開編輯 tab 重置：** 全部存完後離開編輯 tab，回來是乾淨的「選擇照片」；做到一半或有失敗可重試的批次照舊保留。

## 驗證

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，增量建置耗時 1 分 49 秒。175 項 JVM 測試通過，0 失敗／錯誤／跳過（0.7.0 為 153 項），新增存完判斷 3 項、檔名與格式 9 項、編輯成品配對 6 項、切換狀態 4 項；lint 0 錯誤、30 項警告。新增 6 個裝置測試（儲存 3 項含 PNG 來源、存完重置、編輯 tab 首頁、檢視頁切換與翻頁）並更新兩個既有編輯測試的資料夾與數量斷言，**裝置測試只編譯，沒有手機連線，沒有執行**。MediaStore 成對寫入、格子牆與切換都沒在實機驗證。

APK 確認 versionCode 34、versionName 0.7.1、非 debuggable，正式簽章憑證 SHA-256 `e6c756c9…c1217c8`（v2 簽章），16 KB ZIP 對齊與十款富士 LUT 原始 bytes 通過校驗；dex 內確認含 `Pictures/Grain Edits`、`GRAIN_EDIT_` 與新的畫面標記。

成品 `output/Grain-0.7.1-personal-fuji.apk`：14,049,498 bytes，SHA-256 `b416ca19790fbd2f3b2ef513c38b06490bf633c447673c25e9e415b3d5214c8e`。2026-10-04 已推送 main 與 v0.7.1，標籤對應 `8fc3561163581f0af39cfe55511ed0d9715a0906`。已發布 [公開 Release](https://github.com/waltwait/Grain/releases/tag/v0.7.1)，Release ID 403054168；草稿 APK 的大小與 SHA-256 核對後才公開。GitHub 最新版為 v0.7.1、非草稿、非預發布，只有一個 `Grain-0.7.1-personal-fuji.apk`，匿名下載連結回 200。`updates/personal-fuji/latest.json`（提交 `4301804`）在附件確認後才更新為 0.7.1／34，更新說明縮短為一句話；repo 內的檔案與本機產生的完全相同。手機上 0.7.0 的 App 內更新、下載與安裝尚未實測。

## 已知限制

- 每次編輯多佔一份原圖的容量，批次最多 20 張就多 20 份。
- MediaStore 因同名自動改名時（毫秒級重複，極少見）找不到同組原圖，該張改完圖沒有切換；改名後的 `_original (1).jpg` 不會被辨認為原圖，可能出現在編輯成品格子牆。
- 相機「同時儲存原圖」的 `_original` 仍會出現在「照片」tab 格子牆，這次沒有處理。
- 從其他 App 的相簿挑選的照片，Android 可能在 Grain 讀取時移除 GPS 位置（Grain 沒有媒體位置權限），原圖副本是 Grain 讀到的內容，位置資料可能不在其中；尚未在手機驗證。
- 外部相簿 App 會看到 `Grain Edits` 為獨立相簿，裡面同時有改完圖與原圖。
