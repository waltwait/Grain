# Grain 白平衡細調研究

研究日期：2026-10-02。以目前 0.4.4 原始碼與 Android 官方文件為準。本次完成研究與建議，未修改相機功能、版本或 APK；沒有手機可驗證裝置能力與實拍結果。

後續使用者要求提供新版功能，已依本研究實作 0.4.5 的 CCT 操作整理與軟體冷暖／色偏。本文保留 0.4.4 研究時的現況；實作內容與驗證界線見 [0.4.5 白平衡紀錄](white-balance-v045.md)。

## 結論

可以加入更好操作的色溫、綠／洋紅色偏、鎖定與重設。建議先整理既有硬體 CCT 控制，再提供所有支援 Grain 的手機都可使用的軟體冷暖／色偏細調。舊手機的精確 K 白平衡是另一個需要鏡頭校正的工作，不能只換滑桿名稱就宣稱支援。

## 現況檢查

| 項目 | 目前行為 | 建議補強 |
| --- | --- | --- |
| 相機白平衡 | 自動、日光、陰天、陰影、鎢絲燈、螢光燈；依鏡頭能力篩選 | 保留預設，增加可用時的其他預設，不以單一機型寫死 |
| K 色溫 | Android 16／API 36 以上且鏡頭宣告 CCT、AWB OFF 與必要 request keys 才開放 | 用鏡頭實際範圍，加入好控制的步進和微調按鈕 |
| 色偏 | `CaptureSettings.tint` 已有數值；僅在手動 K 時於更多設定顯示 -50～+50 滑桿 | 與色溫放進同一白平衡浮窗，標示綠／洋紅方向 |
| AWB 鎖定 | 有裝置能力檢查；手動曝光時沿用 AWB 鎖定 | 增加直接入口與生效狀態，確認錄影、變焦、切鏡頭行為 |
| 實際回報 | `ActualCapture` 收集 ISO、快門、光圈、K；沒有 tint、AWB state／lock | 區分要求值與實際值，避免裝置限幅時顯示假精度 |
| 軟體調色 | `FilterSettings` 只有 LUT、強度、亮度與輸入編碼 | 增加獨立冷暖／色偏偏移，與硬體白平衡分開保存 |
| 重設 | WB 重設回自動並清除 K／lock，目前未清除 tint | 定義完整重設，避免再次進 K 模式帶回先前色偏 |

程式依據：`camera/CameraCapabilities.kt` 的能力與設定、`camera/CameraEngine.kt` 的 `apply` 與 capture callback、`ui/LiveCameraControls.kt` 的 WB 滑桿／重設、`ui/CameraScreen.kt` 的色偏設定，以及 `gl/LutRenderer.kt` 的共用 shader。

## 三種實作路線

### 1. 直接硬體色溫：優先使用

Android 16 新增 CCT 模式及色溫／色偏 request。必須 AWB OFF；只有 OS 版本達標不代表鏡頭支援。色溫範圍來自鏡頭 metadata，超範圍會限幅。官方規格的最低支援範圍為 2856～6500 K，最大可能範圍為 1000～40000 K；介面不應對所有手機固定顯示完整範圍。[CCT 模式](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#COLOR_CORRECTION_MODE_CCT)、[能力範圍](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE)

硬體 tint 的 -50～+50 對應 D_uv 範圍；特定色溫仍可能再次限幅，需讀取實際 result。此刻度與 Lightroom 等軟體的色偏單位不同；綠／洋紅的介面方向需驗證映射，不能直接照抄其數值或符號。[色溫與色偏 request](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_COLOR_TINT)

### 2. 舊裝置手動 gains／matrix：後續進階工作

Camera2 原本就有 AWB OFF、Bayer RGGB gains 與 sensor 到線性 sRGB 的 transform。應檢查 `MANUAL_POST_PROCESSING`、request／result keys 與 AWB OFF，不能由「支援手動 ISO」推論白平衡也可控制。[手動後處理能力](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)

建議先在 AWB 穩定後取得 gains／transform 作為基準，再加入偏移；有足夠感光元件校正 metadata 時才研究 K 映射。K 到 RGGB 並非通用的螢幕 RGB 黑體色彩公式，各鏡頭的色彩轉換需處理個別校正。[Gains 與 transform](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_GAINS)、[Sensor 校正 metadata](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_CALIBRATION_TRANSFORM1)

這條路線需更多實機資料，第一版不建議在尚未校正的手機上顯示精確 K 數字。

### 3. 軟體冷暖／色偏：涵蓋最多裝置

在已取得的 sRGB 影像加入色彩調整，可支援預覽、拍照、錄影與匯入照片。用相對「冷暖」「色偏」刻度，預設 0；保留自動白平衡，也可在裝置支援時鎖住後微調。sRGB／JPEG 已經套過相機白平衡，未知原本光源或校正資訊時，不應把偏移說成精確的拍攝 K。Adobe 也對非 RAW 影像採相對溫度刻度。[Adobe 白平衡說明](https://helpx.adobe.com/lightroom-classic/desktop/process-and-develop-photos/image-tone-color.html)

工程建議：參數變動時計算色彩轉換係數，在既有 shader 的線性 sRGB 階段、LUT 之前套用，再走同一照片／影片輸出流程。中性設定須保持原結果；避免每幀 CPU 像素處理、重建 LUT、上傳新 LUT texture 或重啟相機。這是預計成本較低的方案，實際耗時仍需量測。硬體已完成白平衡時，軟體只施加使用者另外要求的偏移，避免重複校正。

## 建議的精簡操作

- 主畫面沿用一個 WB 格，不增加常駐按鈕、不縮小觀景窗；點開既有浮窗。
- 浮窗包含「自動／預設／手動」、第一條左右色溫或冷暖滑桿、第二條綠／洋紅色偏滑桿，以及可用時的鎖定與重設。
- 硬體 K 先建議每格 100 K，旁邊 ± 按鈕可微調 50 K；這是介面步進，不是精度保證。接近鏡頭邊界時保留合法端點，顯示實際值或待套用狀態。
- 軟體冷暖用相對值，色偏方向為左綠／右洋紅；以文字與數字輔助顏色。觸控目標至少 48 dp，支援 TalkBack 調整與重設。
- 硬體白平衡鎖定與軟體偏移分開保存；切前後鏡頭或拍照／影片模式後重新確認能力與套用狀態，不沿用不支援的範圍。
- 錄影中可調整是實作目標；即時手勢合併重複值並保留最後值，以免頻繁送 request 或抖動。AWB 鎖定能減少色彩漂移，但跨實體鏡頭切換仍需驗證。

可稍後增加灰卡取樣：用未套 LUT 的中性灰區計算軟體修正，排除過曝、過暗與高飽和樣本。不把它混入目前點擊對焦手勢，也不將其宣稱為感光元件 K 校正。

## 建議實作順序與驗收

1. 整理既有 CCT／tint／lock 介面、完整重設與實際回報，不升級目前 CameraX 1.6.2 到 alpha API。
2. 增加獨立軟體冷暖／色偏，涵蓋無 CCT 的手機與匯入照片；預覽／照片／影片沿用同一組設定。
3. 取得手機能力與實拍後，量測中性設定一致、冷暖／綠洋紅方向、灰階／膚色／高光、LUT 關閉或強度 0 時仍生效、切鏡頭恢復，以及持續錄影與滑動效能。
4. 最後研究經校正的舊裝置手動 K 與灰卡取樣。

目前沒有取得手機的 CCT 範圍、manual-post-processing 能力或結果回報。尚不能承諾某台手機支援硬體 K，亦未量測新增 shader 成本。本研究不需要重新建置現有 APK；後續實作才新增數學／設定測試及裝置 shader／錄影驗證。

## Samsung S24／Android 16 回報補充

使用者確認使用 Samsung S24、Android 16，內建相機可選擇色溫，而 Grain 無法使用。這是 Grain 尚未涵蓋完整白平衡控制路線的回報，不能直接解讀為手機硬體不支援。

原始碼確認 K 控制需要同時通過 API 36、AWB OFF、CCT mode、temperature request key、tint request key，以及有效的 temperature range。任何一項缺少時，WB 滑桿會切回日光等預設。尚未收集這台 S24 的 metadata，因此不能確認是哪一項未通過，也不能確認 Samsung 內建相機使用相同公開介面。[Samsung Pro 模式說明](https://www.samsung.com/ae/support/mobile-devices/how-to-use-the-cameras-pro-mode-on-samsung-galaxy-phones/)

較早的 Camera2 已可在支援手動後處理的鏡頭設定 gains／transform；Grain 目前尚未實作這條路線。現成第三方相機 Open Camera 也提供 Camera2 手動白平衡色溫，說明色溫功能不只限於 Android 16 的新 CCT 介面，但仍不代表這台 S24 的能力已獲驗證。[Open Camera 官方說明](https://opencamera.org.uk/help.html)

下一步先讀取 Grain 設定面板底部「直接色溫」的回報。若有 K 範圍但 WB 沒有 K 滑桿，調查 UI／狀態傳遞；若為「未提供」，再檢查個別 CCT 判斷及舊 Camera2 手動 gains／transform／校正 metadata。保留未確認的原因，不直接移除能力檢查，也不以軟體冷暖偏移冒充已驗證的感光元件 K 控制。
