# Grain 0.4.6 色溫相容修正與介面清理

使用者的 0.4.5 截圖為 Samsung S24（SM-S9210）／Android 16、後鏡頭 ID 0，直接色溫顯示「未提供」。這只證明該鏡頭未提供 Grain 原本使用的 Android 16 CCT 路線，不能推論手機不能手動白平衡。0.4.5 漏了較早的 Camera2 手動白平衡，0.4.6 補上；本次沒有連接手機，不能聲稱 S24 已驗證成功。

## 相機控制

1. 有有效直接 CCT 能力時沿用原本 temperature／tint API 與裝置回報範圍。
2. 否則檢查 MANUAL_POST_PROCESSING、AWB OFF、gains／transform request keys 及 result keys。能力符合時，WB 浮窗也提供「色溫 K」：2000–10000 K 是 App 的估算控制範圍，50 K 步進；色偏為 -50～+50。
3. 相機尚在自動或預設白平衡時，記錄鏡頭實際提供的 RGGB 增益與 3×3 色彩矩陣。手動 K 關閉 AWB／AWB lock，使用 TRANSFORM_MATRIX 並同時送出 gains 和原有色彩矩陣；不把螢幕 RGB 倍率直接當作感光元件增益。
4. 若手動 K 早於第一筆有效相機資料，先保持自動白平衡取得基準，再重送最新設定。等待期間不開始拍照／錄影；逾時顯示一次資料缺失訊息。錄影已進行時仍允許改 K／色偏。
5. 自動或預設會清除舊 gains／transform／CCT keys，回復自動 color correction mode。選擇自動與 WB 重設都沿用此流程，避免回到 AUTO 還殘留手動矩陣。
6. 用匹配目前 request 的 capture result 檢查 AWB OFF、四通道實際增益及矩陣，容許硬體量化。舊請求結果不冒充目前設定已套用；持續不一致會顯示一次非致命訊息。這證明請求是否套用，不能證明 K 值色彩量測準確。

Android 文件說明白平衡由 Bayer 四通道增益與去馬賽克後的 3×3 矩陣組成；在 AWB OFF／TRANSFORM_MATRIX 使用 App 的 gains／transform。相機先前回報的矩陣與增益可用來保持先前白點。[手動後處理能力](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)、[增益與矩陣](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_MODE)。[Open Camera 官方文件](https://opencamera.org.uk/help.html) 也已有 Camera2 手動色溫操作；本版沒有複製其 GPL 原始碼。

## K 值估算與限制

可用 RAW 色彩資料時，以 calibration transform × color transform 將 XYZ 白點轉成 native sensor RGB，兩組參考光源依倒數色溫插值，計算各通道倒數增益。只取得一組時使用單組模型；參考光源未知時不作雙光源插值。[CameraCharacteristics sensor color transform](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_COLOR_TRANSFORM1)、[calibration transform](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_CALIBRATION_TRANSFORM1)。

沒有有效感光元件校正資料時，用回報色彩矩陣的逆矩陣將 XYZ→linear sRGB 白點曲線映回相機通道；把當時 AWB 狀態錨定為估算 5500 K。這個基準不能當作測得的現場光源色溫，切換 AUTO／預設會更新基準。軟體只顯示使用者設定的 K；沒有實際 CCT result 時不假裝鏡頭回報了 K。

白點來自 Planck 光譜在 380–780 nm、5 nm 間距的積分，CIE 1931 使用 Wyman／Sloan／Shirley 的解析擬合係數，Y 正規化為 1。[論文與係數](https://research.nvidia.com/publication/2013-07_simple-analytic-approximations-cie-xyz-color-matching-functions)。色偏使用相對 RGGB 增益補償，正值偏洋紅、負值偏綠。所有請求增益正規化並限制於 Android 保證的 [1,3]，極端 K 或色偏可能限幅。沿用回報色彩矩陣也可能造成不同溫度下的色彩誤差，不能宣稱精確重現 Samsung 內建相機的 ISP／校正或所有鏡頭的實測 Kelvin。

## 介面與效能

- 移除照片檢視器的「雙指縮放 · 雙擊還原」；縮放、雙擊及切換功能保留。
- 移除白平衡滑動／± 操作說明、調色和麥克風說明，以及 LUT 匯入完成後的操作提示。保留錯誤、必要權限請求及無障礙語意。
- 設定的裝置資訊預設收合，展開才顯示版本、鏡頭 ID、色溫路線、手動白平衡回報與儲存位置。舊「直接色溫未提供」不再作為唯一白平衡判斷。
- 濾鏡作者、來源與授權連結保留。
- 色彩計算只在控制改值或白平衡基準資料更新時執行，回報至多約每 300 ms 處理一次；沒有新增每幀 CPU 調色、LUT 重建／上傳或 GPU pass。WB 改值不重綁相機，預覽、照片與錄影共用 Camera2 控制。

## 驗證

十項新增 JVM 測試涵蓋無 CCT 仍選用 gains、基準增益／矩陣保持、Kelvin 與色偏方向、四通道順序、安全限幅、sensor-space 白點、倒數色溫插值、無效相機資料、實際增益比對及獨立 CIE 白點參考。新增測試先因缺少實作編譯失敗，實作後通過。

完整 84 項 JVM 測試通過，0 失敗／錯誤／跳過。Lint 0 錯誤、21 警告，與 0.4.5 相同。Debug APK、AndroidTest APK 與 R8 release 建置通過；最後完整檢查耗時 58 秒。新增裝置 UI 測試檢查沒有 CCT 時的 3000→3050→7500 K 請求、增益／AWB OFF 回報、曝光與 LUT 保持，以及重設後 AWB AUTO；已編譯但沒有執行。

成品 `output/Grain-0.4.6-personal-fuji-debug.apk`，53,246,781 bytes（約 50.8 MiB），SHA-256 `08756b010ab6648a9f44e825cfd32974869a2de79330bc042a9ca48a3d929c20`。沿用 `tw.luma.camera`、versionCode 16／versionName 0.4.6，簽章驗證通過，certificate SHA-256 `322a4b82bbd7b7a23967f5b7703a05cb62f9cf62b6c63ea07b0799d24d99553d` 與 0.4.5 相同。26 個 LUT 相關資產與 0.4.5 逐 byte 相同，個人 debug 內保留十款官方富士 CUBE；release APK 不含富士素材。

本次沒有執行實機 Camera2、照片／影片色彩或流暢度驗證，沒有對手機進行 ADB 安裝或操作。官方富士素材與個人 APK 維持 Git 忽略，只提供私人 prerelease。
