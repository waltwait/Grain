# Grain 0.6.6

點相機左下縮圖後的相簿主頁改為底部「照片／編輯」tab。0.6.5 的 tab 放在單張照片檢視頁，使用者實際要求的是相簿格子頁，因此原主頁仍顯示獨立編輯入口。這版移除主頁頂部的獨立編輯按鈕與相機層的獨立編輯目的地，照片匯入、預覽、濾鏡、強度與原圖比較都留在相簿的編輯 tab。

相簿分類、捲動位置與匯入草稿在切換時保留；匯入草稿使用獨立的 ViewModel key，瀏覽或編輯其他照片也不會重設草稿。離開相簿才清理暫存來源。單張照片原有檢視／編輯 tab 與縮放仍保留。儲存另存新照片，不覆寫原圖，成功不跳出通知。相簿、照片與更新頁返回統一使用 48 dp 箭頭圖示；中文描述僅供輔助功能，沒有可見的中文返回文字。相簿標題與格子留白同步簡化。

版本 0.6.6／versionCode 29，套件 `tw.luma.camera`，非 debuggable。使用 `personal` 建置、原正式簽章與十款富士 LUT；私人 Release 只提供一個 APK，公開下載頁只同步網頁與 metadata。

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 2 分 30 秒。143 項 JVM 測試通過，0 失敗／錯誤／跳過；lintPersonal 0 錯誤、22 警告。裝置測試補充主頁 tab 切換、分類保留、匯入草稿 bitmap／濾鏡在單張照片編輯與 Activity 重建後保持不變，已完成編譯。ADB 裝置清單為空，實際觸控、預覽與覆蓋安裝尚未在手機驗證。

APK 的 DEX 確認包含主頁 `gallery-photo-tab`、`gallery-edit-tab`、`gallery-import-editor` 與圖示返回 `update-back`，舊 `gallery-import-photo` 入口已不在正式 APK。十款富士原始 bytes、共 38 個 LUT 相關／native 檔案與 0.6.5 完全一致，原正式簽章與 16 KB ZIP 對齊通過。

成品 `output/Grain-0.6.6-personal-fuji.apk`：14,016,730 bytes，SHA-256 `5fd445e416ecbb07e8ab15ba5cbec1dd874e6f69266956b81cc6e286368e1b6c`。

2026-10-04 已推送 main 與 v0.6.6，標籤對應 `4e14fcc3f5430a11979a85bcb2e7ea8df25331db`。已發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.6)，附件先以 Release 列表核對草稿的大小與 SHA-256，再發布；GitHub 最新版回報 v0.6.6、非草稿、非預發布，只有一個 APK，14,016,730 bytes 與 SHA-256 和本機成品一致。原 App 儲存庫維持 private=true、has_pages=false。

公開下載頁 metadata 提交 `5bedc3f868261d8690343a936946fdbeb458cbab`，[部署 37182268573](https://github.com/waltwait/Grain-pages/actions/runs/37182268573) 成功。匿名線上 `latest.json` 與本機公開 metadata 完全相同，版本 0.6.6／29、access=github-login，頁面下載按鈕沿用私人最新 Release。公開儲存庫只同步原五個網頁／metadata 檔案，沒有 APK 或 LUT。
