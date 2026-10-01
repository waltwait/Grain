# 0.2.0 大觀景窗、變焦與錄影

2026-10-01。介面參考 [Apple iOS 27 相機說明](https://support.apple.com/guide/iphone/camera-basics-iph263472f78/27/ios/27)與 [iOS 27 新功能](https://support.apple.com/en-ie/guide/iphone/iphfed2c4091/27/ios/27)的模式切換、常用控制與工具面板方向，以原生 Android Compose 實作。

## 已實作

- 主畫面由大觀景窗與精簡快門區構成；LUT 清單、強度、調色、ISO／快門／白平衡移入底部面板。
- 上方提供濾鏡與相機控制快捷鍵；下方提供照片／錄影切換、大快門、相簿與前後鏡頭。
- 倍率按鈕依鏡頭實際範圍生成；可開啟連續變焦滑桿，或直接在觀景窗雙指縮放。可能包含數位裁切。
- 使用 CameraX Recorder 錄影，優先 FHD，依裝置降至 HD／SD；SDR MP4 存入 `Movies/Luma`。實際輸出會依目前觀景窗裁切，不能把 FHD 能力標籤視為必定輸出 1920×1080。
- GPU CameraEffect 同時處理預覽與影片；設定編碼器 EGL 時間戳，將 LUT 寫入影片。
- 錄影時間、停止／儲存狀態；錄影時保留變焦，停用模式與前後鏡頭切換。
- 可選收音，首次需要麥克風權限；未授權可錄製無聲影片。App 進入背景時要求停止並完成存檔。
- 延續個人測試版十款官方富士 LUT 與原有匯入功能；散布與色彩適配限制見 [素材紀錄](personal-fuji-build.md)。

## 本次驗證與暫停點

電腦端 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 成功。24 項 JVM 測試通過，lint 0 錯誤、16 警告（相依套件更新、KTX 建議、data extraction rules）。

真機本輪 9 項測試結果：7 項素材／GPU 測試通過，2 項 UI／錄影測試失敗。錄影測試等待錄製時間超時，變焦面板測試回報沒有 Compose hierarchy。當時的測試輸出也夾有其他 App 的測試與 Activity 切換；目前未確認這是否能解釋全部失敗，不能宣稱錄影端到端驗證通過。

使用者隨後要求「晚點測試」，已暫停真機操作。下次應在手機可專用測試時單獨執行 `CameraExperienceDeviceTest`，先收集錄影開始前後狀態與錯誤，再確認短片可解碼、LUT 實際寫入、無聲／收音流程、旋轉與背景停止。測試應只刪除自身產生的測試片。

## APK

`output/LumaCamera-0.2.0-personal-fuji-debug.apk`：43,172,490 bytes。

SHA-256：`fd44a92c557c590895974873720392f82e8c13a4ce63e9570c4444d9b2d34183`。

目前是待真機驗證的個人開發測試版。
