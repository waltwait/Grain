# Grain 0.6.4

拍照畫面移除「編輯」按鈕，模式列保留「照片／錄影」。相簿內匯入照片與照片檢視頁的編輯入口仍接到既有濾鏡編輯功能，原預覽、強度調整、另存與返回流程沿用。

版本 0.6.4／versionCode 27，套件 `tw.luma.camera`；使用 `personal` build type、原正式簽章與十款富士 LUT。私人 Release 只上傳一個 `Grain-0.6.4-personal-fuji.apk`。公開 Grain-pages 僅同步網頁與成品 metadata，下載按鈕仍連到需登入的私人最新 Release。

本機個人版建置與 lint 完成（2 分 53 秒），lintPersonal 0 錯誤、22 警告。十款富士原始 bytes、共 38 個既有 LUT 資產／native 檔案、原正式簽章及 16KB ZIP alignment 通過核對。這次是按鈕與呼叫參數移除，沒有新增測試。實機畫面及覆蓋安裝尚未測試。

成品 `output/Grain-0.6.4-personal-fuji.apk`：14000346 bytes，SHA-256 `599e36fe339048c7d1889158895937b7543d0ab66d15e9c63f4c81e3910e397e`。

2026-10-04 已推送 main 與 v0.6.4，並發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.4)。GitHub 回報最新版為 v0.6.4、非草稿，只有一個 APK；大小 14,000,346 bytes 與 digest 和本機成品一致。公開網站已推送 0.6.4 metadata，[部署 37174405087](https://github.com/waltwait/Grain-pages/actions/runs/37174405087) 成功。匿名讀取的網站 JSON 和本機成品資訊完全一致；瀏覽器實際顯示 Grain 0.6.4、13.4 MB 與登入下載提示，按鈕仍連到私人最新 Release。原 App 儲存庫再次確認 private=true、has_pages=false。
