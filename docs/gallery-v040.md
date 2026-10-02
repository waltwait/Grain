# Grain 0.4.0 內建相簿

2026-10-02。相簿與瀏覽器以 Jetpack Compose 實作，沿用黑底、暖金與簡化介面。

## 操作

- 點相機左下角縮圖進入相簿；返回按鈕或系統返回可回相機。
- 用「全部／照片／影片」篩選 Grain 儲存的成品，最新項目優先顯示。
- 點照片：左右滑動切換，雙指縮放至 5 倍，放大後可拖曳，雙擊還原。另提供放大／還原和上一張／下一張按鈕。
- 點影片：播放／暫停與進度滑桿。拖曳時只更新滑桿，放開才跳到要求位置，避免每個觸控事件重新 seek。
- 影片不自動播放。分頁滑動時沿用目前播放器，切換完成或離開相簿會釋放播放器；返回同一影片保留本次瀏覽的進度；離開 App 暫停播放，停止時停止播放器並在回到前景時重新準備。
- 空相簿提供匯入照片；沿用現有套用 LUT 後另存流程。

## 流暢度設計

| 部分 | 實作 |
| --- | --- |
| 查詢 | 背景讀取圖片與影片的少量中繼資料；排除尚未寫完的 IS_PENDING 項目 |
| 縮圖 | 只載入懶載入格狀清單所需項目，256×256，最多兩個並行載入；12 MiB LRU 快取 |
| 照片 | 目前頁面才解碼大圖，單一並行解碼；以 ImageDecoder 保留方向處理，限制解碼至 8MP 以下，32 MiB LRU 快取 |
| 分頁 | 使用穩定 URI 作 key；保留格狀清單捲動位置；鄰頁使用縮圖，避免同時解碼多張大照片 |
| 縮放 | 以手指中心作縮放錨點；位移限制在圖片邊界；graphicsLayer 更新縮放與平移，放大時暫停分頁手勢 |
| 影片 | Media3 ExoPlayer 1.11.1＋Compose ContentFrame，自訂 Compose 控制列；只有目前已停留的影片頁建立播放器 |
| 進度 | 250ms 更新小型控制列；使用 repeatOnLifecycle，背景停止輪詢 |
| 相機 | 進入相簿時移除相機組合與釋放 CameraEngine／GPU 預覽；回來重新綁定，不與影片解碼長期並行 |

快取數字是快取持有上限，不是整個 App 的總記憶體保證。畫面仍可持有已淘汰 bitmap；解碼、媒體框架、紋理與播放器也需要記憶體。淘汰時不 recycle 還可能被 UI 使用的 bitmap，避免繪圖讀取已釋放像素。

Media3 1.11.1 是已確認的 stable 發布。ContentFrame 的 UnstableApi 標記只在影片 UI 範圍明確 opt-in，未使用 alpha 版本。

## 範圍

只查詢 Pictures/Grain、Movies/Grain 中由目前 App 擁有的媒體。不要求整個手機的照片／影片讀取權限；MediaStore 允許 Android 10 以上 App 讀取自己建立的媒體。重新安裝後失去所有權的舊檔案、其他 App 成品及早期 Luma 資料夾目前不列入。

這一版提供查看與播放；未加入刪除、分享、編輯或全手機相簿瀏覽。

## 驗證與限制

- JVM 測試驗證解碼分塊倍率、奇數尺寸進位、手指中心縮放計算及拖曳邊界。
- 裝置測試新增相簿篩選與返回相機、建立自己的 JPEG 並在 Grain 中放大及還原；測試只清理自己建立的檔案。
- 手機不在手邊：以上裝置測試只有編譯，尚未執行。影片實際播放／音量／進度跳轉、前後景切換、橫向／大字體、快速翻頁、相機回復與熱機流暢度需實機驗證。

## 官方參考

- [Media3 發布與版本](https://developer.android.com/jetpack/androidx/releases/media3)
- [Media3 Compose UI](https://developer.android.com/media/media3/ui/compose)
- [ExoPlayer 執行緒與資源釋放](https://developer.android.com/media/media3/exoplayer/hello-world)
- [MediaStore 與 App 自有媒體](https://developer.android.com/training/data-storage/shared/media)
