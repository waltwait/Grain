# Grain

原生 Android LUT 拍照 App 第一版。Kotlin、Jetpack Compose、CameraX 與 OpenGL ES 3；支援 Android 10（API 29）以上。

目前開發版本 **0.5.0**，整合現有功能並升版，見 [0.5.0 建置紀錄](docs/release-v050.md)。手機全螢幕拍攝時固定相機介面，橫拿／倒拿只轉動圖示與縮圖；成品方向另由手機方向判斷，影片在開始錄製時固定該段方向。相簿、大螢幕與多視窗保留一般旋轉，見 [相機方向處理紀錄](docs/camera-orientation-v0410.md)。沿用拍照／存檔動畫，成功不跳出提示，見 [拍照與存檔回饋紀錄](docs/capture-feedback-v049.md)。濾鏡沿用 **FUJIFILM／KODAK／GRAIN** 三個英文品牌，各款分別記憶強度，見 [三個品牌分類紀錄](docs/filter-picker-v048.md)。Camera2 手動白平衡見 [色溫相容修正](docs/white-balance-v046.md)。本次未連接手機，方向、動畫體驗、S24 手動色溫與實機色彩仍待驗證。

**0.2.1** 更新使用者確認的黑金底片 icon，支援 Android 自適應遮罩與單色主題圖示；沿用 0.2.0 功能，UI／錄影的待驗證狀態不變。

**0.2.2** 將一般與單色 icon 的圖案縮小 12.5%，增加留白；點擊觀景窗會顯示對焦框與實際完成結果，滑動及雙指縮放不會觸發點擊對焦。自動曝光時一併測光，手動曝光保留 ISO／快門設定。手機端對焦、UI 與錄影驗證仍待進行。

**0.2.3** App 名稱改為 Grain；新照片與影片使用 Grain 相簿及 GRAIN 檔名前綴。保留原有 App 識別碼、設定與匯入 LUT，既有照片不搬移。

**0.2.4** 點擊對焦改用框線縮放與淡出表示，不顯示狀態文字。實際完成後，成功時框線收小、未完成時稍微放大再淡出；連續點擊會取消舊動畫並在新位置回饋。保留螢幕閱讀器的狀態描述。

**0.3.0** 常用相機控制移到觀景窗下方，點擊 ISO／快門／曝光／白平衡／變焦可向上展開直式滑桿。變焦只保留一個倍率按鈕，錄影中也可用滑桿或雙指調整。對焦完成後單指上下滑調整自動曝光補償；手動曝光保留 ISO 與快門設定。詳見 [即時控制與待測項目](docs/live-controls-v030.md)。


**0.4.0** 點左下角縮圖開啟 Grain 相簿，依全部／照片／影片瀏覽。照片可左右切換、雙指縮放、拖曳、雙擊還原；影片提供播放／暫停及進度拖曳。相簿採背景載入與有限快取，瀏覽時暫停相機；LUT 匯入改成串流處理、錄影計時只更新計時區塊。

**0.4.1** ISO／快門／曝光／白平衡使用常駐透明控制列，變焦獨立置中。點控制格向上浮出直式滑桿，開關時按鈕與觀景窗維持位置。頂部改用小型白色圖示與短底片名稱，暖金集中在選取狀態；新增小螢幕、橫向及大字體 Compose 設計預覽。

**0.4.2** 點參數格或變焦鍵，向上浮出橫向滑桿，左右拖動調整。參數名稱與目前值顯示在滑桿上方，AUTO／0／1× 還原放在右側；開關時底部控制與觀景窗維持位置，錄影中仍可調整變焦。

**0.4.3** LUT 在同一次來源讀取中複製原始 bytes、計算 SHA-256 並解析，成功才發布成品。RGB 資料直接掃描，常見小數減少暫時字串與清單；複雜數字與捨入邊界使用標準解析。保留檔案限制及格式校驗，匯入後一次更新選單與目前濾鏡。

**0.4.4** 內建 Daylight、Warm Portrait、Chrome Street、Golden Hour、Night Cinema、Silver，以及 Pat David／Natron 的 Kodak Portra 160／400／800、Ektachrome 100 VS、Tri-X 400 社群模擬。使用預先轉好的二進位 LUT，在背景載入，拍攝沿用既有 GPU 查表；Kodak 項目顯示作者、來源與授權連結。

**0.4.5** 白平衡改用專用浮窗；冷暖／色偏調色不再依賴鏡頭的 CCT 能力。支援直接色溫時可切換 K 控制與冷暖調色，加入 ±50 K、相機預設、可用時的 AWB 鎖定及完整重設。調色在 LUT 前套用，濾鏡關閉或強度為 0 仍生效；照片、影片與匯入照片共用 shader。設定底部顯示版本與手機資訊。

**0.4.6** 補上 Camera2 AWB OFF／RGGB gains／color transform 手動白平衡，不再把缺少 Android 16 CCT 當作沒有手動色溫。公開手動後處理與必要 request／result keys 時提供 2000–10000 K、50 K 微調及色偏；K 為感光元件模型的估算值。優先使用感光元件色彩校正矩陣，否則以鏡頭回報的 AWB 增益／矩陣建立近似控制；檢查實際套用回報，回到自動時清除手動增益與矩陣。移除可見操作說明並收合裝置資訊，保留無障礙描述與濾鏡來源／授權。

**0.4.7** 濾鏡選擇改為觀景窗底部浮層與分類，合併同系列版本。Portra 提供 160／400／800、CLASSIC 提供 Chrome／Neg.、ETERNA 提供一般／Bleach Bypass；切回系列恢復上次版本。只在使用者拖動停住或點選時切換，開啟面板、分類和自動置中不套用第一款。每款強度分別記憶，首次選用預設 100%；匯入使用同一切換流程。作者／來源／授權收進調色資訊，保留原色、匯入、強度與快門直接操作。

**0.4.8** 分類簡化成 FUJIFILM／KODAK／GRAIN 三個英文品牌，移除全部／匯入分類和下方的版本子列；每款濾鏡直接在主列選擇。匯入 LUT 歸入 GRAIN，保留強度記憶、滑動停住套用、來源／授權資訊與大觀景窗。

**0.4.9** 拍照與存檔改用短動畫：接受快門時輕縮，實際拍到影像時觀景窗短暫淡出，確認儲存成功後縮圖淡入並出現短暫金色框。較慢的拍攝／存檔才顯示細進度線；關閉系統動畫時使用靜態回饋。成功存檔與 LUT 匯入／移除不顯示通知，失敗訊息在底部固定區顯示五秒。縮圖讀取失敗不再把已完成的儲存誤判成拍照失敗。

**0.4.10** 手機相機固定直向介面，橫拿／倒拿維持觀景窗、快門及控制列的位置，只旋轉閃光／設定／切換鏡頭圖示、縮圖與變焦數值。以方向感測器獨立決定照片及影片方向，平放時保留最後有效方向，斜角附近加入緩衝避免反覆切換。錄影中只更新圖示，不更動該段影片方向或重新綁定相機；相簿解除相機的方向限制，大螢幕與多視窗沿用自適應介面。

**0.5.0** 將目前拍照／錄影、三個英文濾鏡分類、手動白平衡、內建相簿、安靜存檔動畫與固定相機介面整合為新版 APK。沿用 0.4.10 的功能與原有 App 識別碼、簽章、偏好設定及匯入 LUT；版本號更新為 0.5.0／versionCode 21。

## 功能

- 前後相機、點擊對焦、裝置支援的縮放與 Auto 模式閃光燈。
- `.cube` 3D LUT 匯入、保存、切換、移除，以及 0–100% 強度。
- FUJIFILM／KODAK／GRAIN 三個英文品牌分類，直接選各款濾鏡；滑動停住時套用，保存各款濾鏡強度。
- GPU 即時濾鏡預覽；拍照後以相同 shader 處理照片，分塊輸出 JPEG 到 `Pictures/Grain`。
- 拍攝優先選擇接近 12MP 的尺寸；濾鏡處理在解碼時以二次方降採樣限制在 12MP 以下，降低記憶體用量。原圖選存保留相機原始 JPEG。
- 可同時儲存原圖；EXIF 保留時間與可用拍攝參數，輸出方向正規化。預設不記錄位置。
- 拍照／存檔使用快門與縮圖動畫回饋，成功不跳出通知；錯誤在底部固定區顯示。
- 匯入相簿照片套用目前濾鏡，另存成品。
- 照片／錄影模式；影片套用即時 LUT、可選收音，儲存至 `Movies/Grain`。
- 單一倍率按鈕、向上浮出的橫向變焦滑桿、觀景窗雙指縮放；錄影中仍可調整。常用相機控制常駐觀景窗下方，濾鏡使用底部浮層，更多設定使用底部面板。
- 手機全螢幕相機固定介面，按鈕圖示隨持握方向轉動；照片與影片方向獨立於畫面旋轉設定。
- 裝置能力檢測：Pro 模式快門／ISO、曝光補償、白平衡預設與鎖定，以及可變光圈。
- Android 16 以上且鏡頭公開 CCT 能力時，提供 K 色溫與色偏。
- 沒有直接 CCT、但公開 Camera2 手動白平衡時，提供估算 K 色溫／色偏，並核對相機回報。
- 不需 CCT 的冷暖／綠洋紅調色，可在錄影中調整；與相機白平衡分開，使用相對刻度。
- 繁體中文、深色介面、直橫向適應；相機與處理流程不使用網路。

Grain Originals 是本專案原創調色，適用 sRGB SDR，並非特定底片的量測重現。「暖日、柔霧、銀影」保留為舊版示範色調。Kodak 項目是 Pat David 的社群近似模擬，來源為 [Natron HaldCLUT](https://github.com/NatronGitHub/clut)，依 CC BY-SA 4.0 保留作者與授權；原始素材、版本與轉換說明見 [來源標示](third_party/luts/natron/ATTRIBUTION.txt)。

## 建置

以 Android Studio 開啟此目錄，Sync 後執行 `app`。需要 JDK 17、Android SDK Platform 37.0 與 Build Tools 36.0.0。專案使用 Gradle Wrapper 9.3.1、AGP 9.1.1、Kotlin 2.3.20、Compose BOM 2026.08.00、CameraX 1.6.2；Camera2 interop 集中在相機模組，以 ExperimentalCamera2Interop opt-in 使用。

`local.properties` 是電腦專用設定，不加入 Git。Android Studio 通常會自動建立；手動建置時寫入自己的 SDK 位置：

```properties
sdk.dir=/path/to/Android/sdk
```

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。這是開發測試版，使用 Android debug 簽章；正式上架另需發布簽章與真機驗證。

已建置的第一版另存於 `output/LumaCamera-0.1.0-debug.apk`；測試結果與驗證限制見 [建置紀錄](docs/build-validation.md)。

目前個人富士測試版另存於 `output/Grain-0.5.0-personal-fuji-debug.apk`。APK 與官方富士 LUT 素材不提交 Git；個人測試 APK 另提供私人 GitHub Release 下載。0.1.1 的 19 項 JVM 與 7 項真機裝置測試紀錄見 [個人富士測試版紀錄](docs/personal-fuji-build.md)。

## 安裝與操作

1. 手機開啟開發人員選項與 USB 偵錯，接到電腦；在手機確認偵錯授權。
2. Android Studio 選擇手機並按 Run，或執行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。
3. 第一次啟動允許相機權限；如果沒有相機，仍可從「匯入照片」使用離線濾鏡。
4. 濾鏡浮層的「＋」選擇解壓縮後的 `.cube`；檔案會複製到 App 私有儲存區並放在 GRAIN。點濾鏡卡片或滑動停住時套用，調整強度後可直接按快門。
5. 濾鏡浮層的調色按鈕可設定 LUT 輸入空間、影像亮度及查看作者／授權。「設定」檢視目前鏡頭能力，開啟原圖選存。
6. 點左下角縮圖進入內建相簿；相簿顯示 Grain 建立的成品，照片支援放大及左右切換，影片可播放及拖曳進度。
7. 支援手動曝光時點 ISO／快門格即可進入 M 並調整參數。此時拍攝 EV 停用；調色亮度仍可獨立使用。
8. 主畫面點上方濾鏡名稱選擇 LUT，右上按鈕開啟更多設定；觀景窗下方的 ISO／快門格可直接進入手動曝光，左右拖動滑桿調整，滑桿右上方的 AUTO 會恢復自動曝光。
9. 對焦完成後向上滑變亮、向下滑變暗；單指曝光手勢與雙指變焦分開判斷。手動模式請使用 ISO／快門。
10. 點底部「錄影」再按紅色快門開始，方形停止按鈕完成存檔。錄影中可用倍率按鈕展開滑桿或雙指變焦；首次收音會詢問麥克風權限。

## LUT 與色彩設定

支援純 3D CUBE，2–65 格點、`TITLE`、`DOMAIN_MIN`、`DOMAIN_MAX`、註解與科學記號；紅色索引變化最快。上限 24 MB。1D／混合 shaper LUT 明確拒絕。

一般 creative LUT 使用 `sRGB / SDR`，但應先確認作者指定的輸入／輸出色彩空間符合。`.cube` 副檔名本身不包含完整色彩描述。

一般原始碼與 release 版本採使用者自行匯入富士素材。依目前個人測試需求，另提供本機 debug 素材準備流程：保留官方檔案原樣，內建十款 F-Log2 33 格點 LUT，畫面會自動套用 F-Log2 近似適配。來源與授權研究見 [研究文件](docs/android-lut-camera-research.md)。

```sh
python3 scripts/prepare_personal_fuji_luts.py /path/to/gfx-eterna-55-3d-lut-v110.zip
./gradlew :app:assembleDebug
```

ZIP 從 [富士官方 LUT 下載頁](https://www.fujifilm-x.com/global/support/download/lut/)取得。腳本校驗已研究版本的 SHA-256，將原始 CUBE 與來源校驗紀錄放進被 Git 忽略的 `app/src/debug/assets/luts/fujifilm`，不會放入 release APK。debug APK 含有官方素材，不應當作已取得公開散布或商用授權的版本。詳情見 [個人富士測試版紀錄](docs/personal-fuji-build.md)。

已提供 **F-Log／F-Log2／F-Log2C 近似適配**，可以實驗套用官方 film-simulation LUT：

- 將手機 SDR 的 sRGB 解碼為線性值，以近似場景亮度處理。
- 依官方原色座標轉到 F-Gamut／F-GamutC，再編碼相應 Log 曲線。
- 套用 LUT，假設其輸出為 Rec.709 原色與 display gamma 2.2，轉回 sRGB。
- 與原圖在共同的 sRGB 輸出空間混合強度。

這不能反轉手機 ISP 的 tone mapping，也無法補回已裁切高光，不代表富士相機完整成像。請使用 film-simulation／display-output LUT；輸出仍是 Log 的技術轉換表不適用這個假設。已知 `#Gamma:` 標頭會提示輸入模式，仍應檢查「調色」中的設定。

公式來源：[F-Log2 資料表](https://dl.fujifilm-x.com/support/lut/F-Log2_DataSheet_E_Ver.1.1.pdf)、[F-Log2C 資料表](https://dl.fujifilm-x.com/support/lut/F-Log2C_DataSheet_E_Ver.1.0.pdf)、[GFX ETERNA 55 白皮書](https://dl.fujifilm-x.com/support/lut/GFX_ETERNA_WhitePaper_260206_v101.pdf)。官方 LUT 的商用／散布權需另行確認。

## 實作結構

```text
app/src/main/java/tw/luma/camera/
  MainActivity.kt           Compose 入口與主題
  CameraViewModel.kt        拍照、匯入、濾鏡狀態與儲存流程
  camera/                  CameraX、Camera2 interop、能力與實際參數
  gl/                      EGL、GPU LUT shader、預覽 Surface 與分塊輸出
  lut/                     CUBE 解析、CPU 三線性參考、自製色調
  storage/                 JPEG、EXIF、MediaStore 原子存檔與回滾
  ui/                      相機畫面、手動面板與權限狀態
```

## 驗證範圍與待驗項目

JVM 測試覆蓋 CUBE 索引順序、三線性內插、domain、65 格點、損壞檔案、色域矩陣、白平衡方向／中性亮度及重設。另附裝置 GPU／操作測試，檢查 shader 色彩、方向、強度、分塊邊界、白平衡套用順序及錄影控制；連接手機後執行 `./gradlew :app:connectedDebugAndroidTest`。不能以 CPU 測試取代 GPU、鏡頭或畫質驗證。

真機請依 [驗收清單](docs/device-validation.md)檢查。特別是：

- 固定光圈鏡頭不能調整光圈；不支援手動參數的鏡頭不開放 Pro。
- M 模式自動白平衡在裝置支援時鎖定；更完整的舊裝置 K 色溫校正與半自動優先模式尚未實作。
- ISO／快門滑桿代表要求值；預覽上方顯示裝置回報值。需驗證照片 EXIF 與請求一致。
- 相機預覽與 JPEG 可能採用不同 ISP 處理，即使共用 shader，成品仍可能有差異。
- 慢快門預覽會降幀。GPU ES 3、記憶體、持續使用溫度與閃光燈行為需逐機測試。
- 匯入照片目前限制 50MP 以下，超過 12MP 會降採樣；輸出為 SDR sRGB JPEG，沒有 RAW／HDR 保留流程。
- 第一次版本只切換前後預設鏡頭；尚未提供所有實體超廣角／望遠鏡頭選擇。

現階段不需要 Vercel。日後若另加官網或雲端服務，建議安裝 `npm i -g vercel`，以使用 `vercel env pull`、`vercel deploy` 與 `vercel logs`。
