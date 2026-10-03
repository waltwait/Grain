# Grain 0.4.10 固定相機介面

依使用者要求，手機橫拿或倒拿時，拍攝介面維持原來的位置；只有按鈕內容隨手機方向轉動。全螢幕手機相機要求固定 portrait，沒有更換觀景窗版型、旋轉整個 Compose 畫面或新增操作說明。閃光、設定、切換鏡頭、底片圖示、影片品質數字、變焦數值及相簿縮圖使用 graphicsLayer 轉動，按鈕觸控範圍固定；模式列、手動控制列及展開的設定面板維持位置。

旋轉採 160 ms 短動畫，以目前角度計算最近的等效角度，跨越 0°／360° 時不繞一整圈。動畫使用 Compose 的系統時間倍率；關閉系統動畫時直接到位。方向感測器只在相機可見且 Activity resumed 時啟用，離開相機或切到背景即停止；每次回報先量化成四個方向，只在方向真的改變時更新 UI。相鄰方向間有 10° 緩衝，斜角附近不會反覆切換，平放或方向不明時保留最後有效方向。

## 成品方向與視窗

先前照片與影片直接使用 display rotation。固定螢幕後不能繼續靠這個值判斷橫拍，所以新增獨立的 capture rotation：按快門時套用到 ImageCapture，開始錄影時套用到 VideoCapture。Preview 沿用視窗方向；持握方向變化不重新綁定相機，也不修改正在錄製的 VideoCapture target rotation。下一次拍照／錄影使用最新的手機方向，已開始的影片維持開始時方向。原有 LUT 的 SurfaceOutput transform 與 JPEG EXIF 正規化流程沿用既有實作。

這個方向處理依據 [CameraX 固定介面的方向指南](https://developer.android.com/media/camera/camerax/orientation-rotation) 與 [VideoCapture 的 target rotation API](https://developer.android.com/reference/androidx/camera/video/VideoCapture#setTargetRotation(int))；[OrientationEventListener](https://developer.android.com/reference/android/view/OrientationEventListener) 的四個方向相對於裝置自然方向，UI 另扣除目前 display rotation，避免在自然橫向裝置或已旋轉視窗中再次旋轉圖示。

進入 Grain 相簿會解除相機的 portrait 要求，返回相機時重新套用。Activity 重建時保留相機／相簿位置及最後拍攝方向。大螢幕（smallest width ≥ 600 dp）與多視窗不要求固定 portrait，沿用既有自適應版型；這也符合 [Android 大螢幕會忽略部分方向限制的行為](https://developer.android.com/about/versions/16/behavior-changes-16#large-screen-adaptivity)。這次沒有實機量測，因此不宣稱各種視窗或 OEM 的旋轉行為已驗證。

## 驗證

新增九項 JVM 測試，涵蓋四方向 CameraX rotation、平放／無效感測值、斜角與跨零點緩衝、跳過中間方向、旋轉視窗的圖示補償、感測器缺失時的起始方向、最短動畫路徑，以及手機／相簿／大螢幕／多視窗範圍。完整 **110 項 JVM 測試通過**，0 失敗／錯誤／跳過。Lint **0 錯誤、21 警告**，與 0.4.9 相同；SourceLockedOrientationActivity 僅在明確的相機視窗策略方法上抑制，註明這是使用者要求的相機行為，其餘頁面與視窗維持自適應。

Debug APK、AndroidTest APK 與 R8 Release 全部建置成功，完整檢查耗時 **1 分 3 秒**。新增「手機相機要求 portrait → 進入相簿解除要求 → 返回相機恢復要求並就緒」裝置操作測試，已編譯、未執行。新增 360 × 640 的側拿與倒拿 Compose 設計預覽，已編譯，未實際渲染截圖。

沒有連接手機、操作 ADB、驗證感測器、拍攝方向、影片 metadata 或執行 GPU／幀率量測。S24 請確認直拿、左右橫拿、倒拿的拍攝介面與成品方向；橫向開始錄影後改變持握方式應維持同一段影片方向，下一段應採新的方向。相簿旋轉、返回相機、背景恢復與系統動畫關閉仍待實機確認。

## 個人 APK

成品 `output/Grain-0.4.10-personal-fuji-debug.apk`，**54,080,657 bytes**（約 51.6 MiB），SHA-256 `a0517ba4cdadc83f459f655b4435ab54c46e26d53db12d3dfc1677def44a1e83`。沿用 `tw.luma.camera`、versionCode 20／versionName 0.4.10。簽章驗證通過，certificate SHA-256 `322a4b82bbd7b7a23967f5b7703a05cb62f9cf62b6c63ea07b0799d24d99553d` 與 0.4.9 相同，可直接更新。

26 個 LUT 相關資產與 0.4.9 逐 byte 相同，個人 debug 保留十款官方富士 CUBE；Release 不含官方富士素材。素材與個人 APK 維持 Git 忽略，只提供私人 prerelease。
