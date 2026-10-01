# 個人富士 LUT 測試版

版本 0.1.1（versionCode 2），2026-10-01。使用官方 GFX ETERNA 55 Ver.1.10 壓縮包中的 F-Log2 33Grid 素材。

十款：CLASSIC CHROME、CLASSIC Neg.、REALA ACE、PROVIA、Velvia、ASTIA、PRO Neg. Std、ETERNA、ETERNA Bleach Bypass、ACROS。

官方 CUBE 不修改、不重新取樣。內建選單名稱由 App metadata 提供，輸入設定由原始 `#Gamma:` 標頭識別。使用目前 sRGB → 近似場景亮度 → F-Gamut → F-Log2 → LUT → sRGB 流程，效果仍需以手機畫面、膚色、灰階與高光校正，不代表重現富士機身成像。

## 素材與建置

[官方下載頁](https://www.fujifilm-x.com/global/support/download/lut/)；ZIP SHA-256：`febfc7050999620651ca0cf162bf8b499970ef270b4da632765b61b958cf7940`。

`scripts/prepare_personal_fuji_luts.py` 檢查 ZIP 指紋並抽出十款完整原始檔案；每個檔案的 SHA-256 與包內路徑記於本機 `sources.json`。素材僅位於 debug source set，被 Git 忽略；一般 release 保留匯入功能。

免費下載不等同開源授權。這次按使用者要求準備本機個人測試版，沒有確認公開重包散布或商用權利。公開發布前應向富士確認素材授權，或改用可明確散布的自製素材與使用者匯入流程。[網站使用條款](https://global.fujifilm.com/en/terms)與 [研究紀錄](android-lut-camera-research.md)提供目前查核證據。

## 後續優先順序

1. 真機 GPU 與素材載入：identity、65 格點、強度、domain、分塊方向，以及十款實際素材的獨立 CPU 灰階參考。
2. 相機端到端驗收：前後鏡頭、旋轉、背景恢復、拍照、EXIF、存檔與原圖選存。檢查要求與回報的 ISO／快門一致。
3. 手機色彩調整：用同一場景的膚色、灰階、色卡與高光比較預覽及照片；改善 SDR 到 Log 的近似曝光標定。
4. 日常使用體驗：濾鏡收藏、常用配方保存、縮圖、倒數計時；在基礎畫質穩定後再加入顆粒與暈光。
5. 發布準備：多機型相容性、持續拍攝效能、正式簽章與素材授權。

## 本次已完成的驗證

執行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` 建置成功。

- JVM：19 項通過，0 失敗。
- Samsung SM-S9210、Android 16：7 項裝置測試通過，0 失敗、0 跳過。涵蓋十款素材載入、十款實際 LUT 的 GPU 灰階與 CPU 參考比對，以及原有 5 項 shader／圖片方向／分塊測試。
- lint：0 錯誤、15 警告；類型同 [第一版建置紀錄](build-validation.md)。
- APK 中檢查到 10 個官方 CUBE，debug v2 簽章驗證通過。
- 已透過 `adb install -r` 更新使用者手機，回報 `Success`；`am start -W` 回報 `Status: ok`，程序持續執行。沒有以這個啟動檢查宣稱相機預覽、拍照或畫質驗收完成。

檔案：`output/LumaCamera-0.1.1-personal-fuji-debug.apk`，43,090,482 bytes（約 41.1 MiB）。SHA-256：`e987cb95a76df4b5caf94bc0b9b8564d6eaadd1615d62f2f07de90c1a7796a67`。

尚待執行：實際拍攝、曝光／白平衡生效、JPEG 與預覽一致性、相簿儲存與原圖選存、旋轉及生命週期、持續使用記憶體與溫度測量。
