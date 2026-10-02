# Grain 0.4.5 白平衡細調

使用者回報 Samsung S24／Android 16，內建相機可調色溫，但尚未更新 Grain。本版提供最新白平衡介面與不依賴 CCT 的冷暖／色偏調色。本次沒有連接或安裝手機，不能以此證明 S24 的硬體 K 控制已驗證。

## 使用方式

1. 點觀景窗下方「白平衡」，浮窗不改變觀景窗或底部控制的位置。
2. 沒有直接 CCT 能力時，顯示「冷暖 · 冷 ↔ 暖」與「色偏 · 綠 ↔ 洋紅」，皆為 -100～+100 的相對調色刻度；預設 0。
3. 有 CCT 能力時，可切換「色溫 K」和「冷暖調色」。K 使用鏡頭回報範圍，滑動以 50 K 量化，± 按鈕微調並保留合法端點；第一次移動後固定 K，才可操作相機色偏。
4. 「相機白平衡」選單保留自動與鏡頭提供的日光等預設。相機在 Auto 且支援鎖定時，浮窗也可鎖定 AWB；M 模式沿用既有自動鎖定。
5. 「重設」回到自動、清除硬體 K／tint／使用者鎖定與軟體冷暖／色偏，保留 ISO、快門、光圈、變焦、LUT、強度及影像亮度。浮窗維持開啟，便於確認數值。
6. 另可在「調色設定」調整冷暖與色偏，用於匯入照片；這裡的「重設冷暖與色偏」只清除軟體偏移。

錄影進行中可以操作 WB；開始／停止錄影及照片處理中，主畫面控制沿用原有停用規則。窄螢幕、橫向及大字體時浮窗可捲動。滑桿保留左右方向、數字及 TalkBack 描述，微調／重設觸控目標至少 48 dp。

## 白平衡與調色的界線

直接 K 仍要求 Android 16／API 36、AWB OFF、CCT mode、temperature／tint request keys 及有效鏡頭範圍；本版沒有繞過能力檢查。硬體 K／tint 可能限幅，浮窗在有 result 時顯示鏡頭回報。[Android CCT](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#COLOR_CORRECTION_MODE_CCT)、[色溫與色偏規格](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_COLOR_TINT)

軟體冷暖／色偏作用在手機 ISP 已處理的影像上，沒有宣稱為感光元件 K 校正，不會額外改寫拍攝 ISO 或快門。相機白平衡與軟體調色可同時存在，但分開保存設定，不自動重複施加同一校正。調色偏移留在目前工作階段；重啟 App 預設回到 0，沿用既有 LUT 偏好保存方式。

Samsung 內建相機可調色溫不等於公開 CCT API 一定提供，較早 Camera2 的 gains／transform 也是可能路線。本版尚未實作經校正的舊裝置 K。設定面板底部顯示 Grain 版本、手機型號、Android 版本與直接色溫範圍，供之後 S24 實測。[前次研究](white-balance-fine-control-research.md)

## 影像與效能

`FilterSettings` 新增 `warmth`／`tint`。調整值改變時計算線性 sRGB 的三通道倍率，正冷暖增加相對紅、減少相對藍；正色偏增加相對紅／藍、減少綠。以 Rec.709 亮度權重正規化，讓中性灰的未裁切線性亮度保持一致。此為原創相對調色；高飽和色與亮部仍可能裁切，不能恢復原本遺失的高光或反轉 ISP 處理。

倍率僅在 renderer 設定改變時更新 uniform；每幀沒有重新生成 LUT、上傳 LUT texture、逐像素 CPU 調色或新增離屏 pass。預覽與影片沿用 `LutSurfaceProcessor`，照片及匯入照片沿用 `LutRenderer.apply`，均在亮度調整的同一線性 sRGB 步驟、LUT 之前套用，再執行原本的 LUT 強度混合。0／0 中性設定跳過新增調色，保留舊色彩流程。

照片 no-op 判斷已納入冷暖／色偏，避免未選 LUT 或強度為 0 時跳過照片調色；LUT 強度為 0 也不會跳過預覽／影片的冷暖調整。實際 GPU 額外成本與連續錄影流暢度仍需真機量測。

## 驗證

- 九項新增 JVM 測試涵蓋冷暖／綠洋紅方向、中性設定、灰階線性亮度、有效數值／範圍、照片 no-op 分支、完整重設與非整數邊界 K 量化；首次執行九項中六項失敗，完成實作後通過。
- 完整 74 項 JVM 測試通過，0 失敗、錯誤或跳過。Lint 0 錯誤、21 警告，與 0.4.4 相同。
- Debug APK、AndroidTest APK、R8 精簡後未簽章 release 建置通過。
- 新增三項裝置 GPU 測試：WB 單獨輸出、先 WB 再 LUT／強度 0／Log 跳過，以及同一 renderer 改值／重設時的 uniform 更新。
- 裝置 UI 測試加入 WB 微調／重設保留曝光與選取 LUT，以及錄影中調整 WB 後仍持續錄影；另更新無 CCT、K 控制、橫向與大字體的 Compose 設計 fixtures。
- 裝置 GPU、UI、實際 JPEG／影片色彩與 S24 直接 K 支援尚未執行；不能以編譯或 JVM 測試取代實機結果。

個人 debug APK 沿用 `tw.luma.camera` 與原有簽章，versionCode 15、versionName 0.4.5，保留十款官方富士 LUT、五款 Kodak 社群模擬及六款 Grain Originals。官方富士素材與 APK 不提交 Git；個人包只上傳私人 prerelease。

成品為 `output/Grain-0.4.5-personal-fuji-debug.apk`，53,708,441 bytes（約 51.2 MiB），SHA-256 `80bc54f8e92550a3186fb3233d193bf47cd0fa3334843ebb62397a9e8d5d64a4`。簽章驗證通過，certificate SHA-256 與 0.4.4 相同；26 個 LUT 相關資產逐 byte 相同，release 內沒有官方富士 CUBE。最後完整建置／檢查通過，耗時 1 分 5 秒。
