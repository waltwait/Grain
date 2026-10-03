# Grain 0.6.0 建置紀錄

依使用者要求將 versionName 由 **0.5.0** 升為 **0.6.0**，versionCode 由 **22** 升為 **23**。套件仍為 `tw.luma.camera`，沿用既有偏好、匯入 LUT 與資料格式，不需要資料搬移。正式簽章沿用 0.5.0 Release 的 key，certificate SHA-256 為 `e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8`。

## 內容

- 獨立「編輯照片」頁：從相機下方或相簿進入，預覽濾鏡、調整強度及比較原圖後按儲存；另存新照片，不改相機濾鏡、不覆蓋原圖、不跳出儲存通知。預覽與輸出限制見 [照片編輯紀錄](photo-editor-v050.md)。
- 觀景窗左右滑動變焦，拍照及錄影皆可使用；保留點擊對焦、對焦後上下滑曝光與雙指縮放。
- 參數面板半透明，調整時進一步淡化，放開後恢復。
- 相簿以手勢瀏覽照片，移除可見放大／還原與上一張／下一張按鈕。
- 正式成品內建六款 Grain Originals 與五款 Kodak 社群模擬，保留作者與授權；官方富士 CUBE 沿用 debug source set 的個人測試用途，不加入 Release。

## 驗證

`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest`、`assembleRelease`、`bundleRelease` 全部成功，耗時 **53 秒**。**126 項 JVM 測試通過**，0 失敗／錯誤／跳過；Lint **0 錯誤、20 警告**。裝置測試完成編譯，本次未連接手機或執行實機測試。

APK metadata 與 AAB base manifest 均確認為 `tw.luma.camera`／0.6.0／23，非 debuggable。APK 簽章驗證通過，certificate 與 0.5.0 Release 相同；`zipalign -c -P 16 4` 通過。11 個 LUT 與 12 個 native library 均與先前驗證的 0.5.0 成品逐 byte 相同；APK／AAB 的 LUT 亦一致。

AAB 的 JAR 簽章與 bundletool 結構驗證通過。JDK jarsigner 的自簽 certificate、無 timestamp、POSIX attributes 與 AGP manifest 排列提示仍存在，細節沿用 [Release 準備紀錄](release-publishing-v050.md)，沒有自行重排或重新產生 key。R8 後的相機、錄影、S24 白平衡、GPU 預覽與編輯輸出仍需實機驗證。

## 成品與草稿

| 成品 | Bytes | SHA-256 |
| --- | ---: | --- |
| `output/Grain-0.6.0-release.apk` | 11,700,290 | `7ba9e955ce461954a86d07284aebf38beee6d53e8902587751898c6c25832fd2` |
| `output/Grain-0.6.0-release.aab` | 13,133,862 | `c152c0f3d813bc3b0f1d9ff1dbdc286b8273c72e78fea0376c96b877b36a13d6` |

各有 `.sha256` 檔；R8 mapping 另存為 `output/Grain-0.6.0-release-mapping.txt`。成品與 `.signing` 均不提交 Git。

GitHub 原正式版草稿升為 `v0.6.0`，保留草稿狀態，附上本版 APK／AAB 與校驗檔；私人儲存庫可見性與已發布的 `v0.5.0` 個人測試版保持原樣。發布管道與既有 keystore 的確認沿用前次待辦，未公開發布或上傳 Google Play。
