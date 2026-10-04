# Grain 0.6.8

相簿主頁的獨立編輯 tab 改用圖片多選器，一次最多 20 張，也可只選一張。上方縮圖列切換預覽來源，所有選取照片共用濾鏡與強度；切換預覽、照片／編輯 tab 與 Activity 重建保留草稿。重新選取來源建立新的編輯草稿。

按右上勾號後逐張另存照片，不覆寫來源。進度與成功／失敗數量顯示在編輯頁；停止會完成當前照片再停止，保留已完成的輸出。「繼續／重試」跳過已成功的來源，只處理未完成照片。修改濾鏡或強度則開始新的整批設定。儲存期間停用 tab、來源切換和調整參數；沒有跳出通知或存檔路徑提示。

每次只載入當前預覽與一張輸出來源。縮圖依可見項目在背景讀取 128px；預覽沿用 1.5MP／2048px 限制，完整來源沿用 80MB／50MP 輸入限制，輸出沿用 12MP 上限。逐張完成後刪除該張暫存來源；整批只重新整理相簿及相機縮圖一次。縮圖列與濾鏡列吸收水平捲動邊界的剩餘手勢，避免切走主頁 tab。

來源由 Photo Picker 或其文件選擇器 fallback 授權，程式再檢查去重與 20 張上限。只持久化本次新增的讀取授權，重新選取或離開編輯時釋放；不要求全相簿權限。API 行為參照 [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)。這是 App 內的批次操作，沒有新增背景工作／通知服務，也不會在程序重啟後自動執行。

版本 0.6.8／versionCode 31，沿用個人版、正式簽章、套件與十款富士 LUT。2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 2 分 33 秒。153 項 JVM 測試通過，0 失敗／錯誤／跳過；新增 10 項批次測試涵蓋序列處理、錯誤隔離、跳過成功照片的重試、停止與繼續、取消傳播、去重及選取上限。lintPersonal 0 錯誤、30 警告；新增警告是 `String.toUri` KTX 寫法建議，沒有新增功能性錯誤。

裝置測試新增多來源預覽、共用濾鏡／強度、Activity 重建、兩張成功／一張無法讀取的部分成功、原檔 bytes 不變、輸出 EXIF 與失敗重試不重複輸出，已完成編譯。ADB 裝置清單為空，系統多選器、實機預覽、觸控與覆蓋安裝尚未驗證。

APK DEX 已確認批次進度、停止、重試 UI 標記與既有 gallery tab 標記，舊獨立編輯按鈕標記不存在。十款富士 LUT 原始 bytes、共 38 個 LUT／native 檔案與 0.6.7 完全相同；原正式簽章與 16 KB ZIP 對齊通過。

成品 `output/Grain-0.6.8-personal-fuji.apk`：14,033,114 bytes，SHA-256 `76ecafb751a5c090154f50668fbc79d9ca1cf42511b892d7ef47add1fd514528`。

2026-10-04 已推送 main 與 v0.6.8，標籤對應 `db76d19e988de5cd931e3968779e7430c29c6ade`。已發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.8)，Release ID 402891473；先核對草稿單一 APK 的大小與 SHA-256，再發布。GitHub 最新版為 v0.6.8、非草稿、非預發布，只有一個 `Grain-0.6.8-personal-fuji.apk`；大小、SHA-256 與發布後下載網址均和本機 metadata 相同。App 儲存庫維持 private=true、has_pages=false。

公開下載頁 metadata 提交 `e7815b69f79684ba774e5d63047639b17764147f`，[部署 37184863490](https://github.com/waltwait/Grain-pages/actions/runs/37184863490) 成功。匿名線上 `latest.json` 與本機公開 metadata 完全相同，版本 0.6.8／31、access=github-login，下載按鈕沿用私人最新 Release。公開儲存庫仍只有六個既有網頁／說明／metadata 檔案，沒有 APK 或 LUT。
