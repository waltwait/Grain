# Grain 0.6.7

相簿主頁「照片／編輯」tab 改為共用 HorizontalPager 狀態。可以左右滑動切換，也能點底部 tab 平順切換；選取標記、頁面、返回與儲存按鈕由同一頁碼決定。分類、格子捲動位置與匯入草稿保留；Activity 重建恢復所選 tab。

編輯面板的水平巢狀捲動吸收濾鏡列到邊界後的剩餘位移及速度，濾鏡選擇不會帶動外層 tab。強度滑桿沿用自己的觸控操作。滑動尚未結束時不能啟動儲存，儲存期間停用頁面手勢與 tab；沒有加入可見手勢說明或儲存通知。

版本 0.6.7／versionCode 30，套件 `tw.luma.camera`；沿用個人版、原正式簽章與十款富士 LUT。單張照片頁原本的左右換照片、縮放與照片／編輯點擊切換沿用，這次修改相簿格子主頁的 tab 切換。

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 2 分 15 秒。143 項 JVM 測試通過，0 失敗／錯誤／跳過；lintPersonal 0 錯誤、22 警告。裝置測試補充左右滑動與點擊切換、分類保留、匯入預覽與濾鏡保留、主頁 Activity 重建、濾鏡列邊界與強度手勢留在編輯 tab，已完成編譯。ADB 裝置清單為空，實機觸控與覆蓋安裝尚未驗證。

APK DEX 包含 `gallery-tabs-pager`、`gallery-photo-tab`、`gallery-edit-tab`、`gallery-import-editor` 與 `update-back`，舊 `gallery-import-photo` 不存在。十款富士 LUT 原始 bytes、共 38 個 LUT／native 檔案與 0.6.6 完全相同；原正式簽章與 16 KB ZIP 對齊通過。

成品 `output/Grain-0.6.7-personal-fuji.apk`：14,016,730 bytes，SHA-256 `75609e72b83b4c0a57697934e8ea09ba47197297b284a451bfdb7d1732df946e`。

2026-10-04 已推送 main 與 v0.6.7，標籤對應 `694d26b9d7c33c9ce83797fca53fe517aab0fa55`。已發布 [私人 Release](https://github.com/waltwait/Grain/releases/tag/v0.6.7)，Release ID 402879708；草稿核對附件大小與 SHA-256 後才發布。GitHub 草稿附件的 temporary untagged URL 於發布後轉為正式 v0.6.7 URL，最終網址也已與 metadata 核對一致。最新版為 v0.6.7、非草稿、非預發布，只有一個 APK，大小與 SHA-256 和本機成品相同。App 儲存庫維持 private=true、has_pages=false。

公開下載頁 metadata 提交 `068e722f04b9269324b98abdde5474896bb60ffe`，[部署 37183312032](https://github.com/waltwait/Grain-pages/actions/runs/37183312032) 成功。匿名線上 `latest.json` 與本機公開 metadata 完全相同，版本 0.6.7／30、access=github-login，下載按鈕沿用私人最新 Release。公開儲存庫仍只有六個既有網頁／說明／metadata 檔案，沒有 APK 或 LUT。
