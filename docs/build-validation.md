# 第一版建置紀錄

驗證日期：2026-10-01。版本：0.1.0。套件名稱：`tw.luma.camera`。

## 已執行

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

- 建置成功，產出 debug App APK 與裝置測試 APK。
- JVM 共 19 項測試通過，0 失敗、0 錯誤、0 跳過。
- CUBE 解析／內插共 16 項；色域矩陣共 3 項。
- Android lint：0 錯誤、15 警告。警告為 6 項相依套件更新提示、8 項 KTX 寫法建議，以及 1 項 data extraction rules 建議；目前應用設定 `allowBackup=false`，尚未配置 Android 12 以上的裝置轉移規則。
- `apksigner verify --verbose` 成功，APK 使用 debug 金鑰與 v2 簽章。

## 交付檔案

- `output/LumaCamera-0.1.0-debug.apk`：40,190,088 bytes（約 38.3 MiB）。
- SHA-256：`96108e9dc8b19c004fa8e211ba30b7167df6e0e5d5cc5975fa8b0f3598454c3a`。
- 同目錄提供 `.sha256` 校驗檔。

## 尚未驗證

本次沒有連接 Android 裝置，也沒有可用的模擬器。5 項 GPU 裝置測試已編譯，尚未執行。App 的實際啟動、CameraX Surface 處理、shader 在不同 GPU 的支援、拍攝參數生效、色彩與照片儲存均待真機驗證。

連接手機後先執行：

```sh
adb install -r output/LumaCamera-0.1.0-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

再依 [真機驗收清單](device-validation.md)逐項測試。編譯與 JVM 測試通過不能視為相機硬體驗收完成。
