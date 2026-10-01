# Android LUT 拍照 App 可行性研究

研究日期：2026-10-01（Asia/Taipei）。範圍：來源、授權證據、色彩管線、Android 架構、手動拍攝控制與第一版驗證計畫。本次未建立 App、未量測手機效能。

## 結論與產品方向

可以開發。建議做原生 Kotlin + Jetpack Compose 相機，支援匯入 `.cube`、即時預覽及完整解析度照片輸出。第一版聚焦離線 SDR 拍照；富士官方 Log LUT 的手機適配列為獨立色彩實驗。

工程可行性高，但「得到好看的富士風格」與「精準重現富士機身成像」是不同驗收目標。後者涉及感光元件、白平衡、機身影像處理及調色曲線，現有 LUT 不能保證跨手機重現。

## 1. 富士到底提供了什麼

[富士官方 LUT 下載頁](https://www.fujifilm-x.com/global/support/download/lut/)提供按機型區分的 `.cube` 壓縮包。本次直接下載並檢查 GFX ETERNA 55 Ver.1.10，官網列出的更新日期是 2026-04-16。

本次下載的[官方壓縮包](https://dl.fujifilm-x.com/support/lut/gfx-eterna-55-3d-lut-v110.zip)包含 56 個 `.cube` 與 6 個說明 PDF，分成 33Grid／65Grid、F-Log／F-Log2／F-Log2C。SHA-256：`febfc7050999620651ca0cf162bf8b499970ef270b4da632765b61b958cf7940`。檔案僅下載到暫存目錄，未加入專案資產。

F-Log2 與 F-Log2C 資料夾各提供以下 10 種模擬，另有 2 種技術轉換；33／65 格點是同系列用途的不同取樣密度，不能算成不同濾鏡：

- PROVIA、Velvia、ASTIA、CLASSIC CHROME、REALA ACE。
- PRO Neg. Std、CLASSIC Neg.、ETERNA、ETERNA BLEACH BYPASS、ACROS。

這個包的 F-Log 資料夾只有 ETERNA、ETERNA BLEACH BYPASS 與兩種技術轉換，不具有上述完整十款。不要把 ETERNA 55 的內容推廣成所有富士機型下載包的內容。

官方[白皮書，第 13–18 頁](https://dl.fujifilm-x.com/support/lut/GFX_ETERNA_WhitePaper_260206_v101.pdf)也確認 F-Log2／F-Log2C 可套用十種底片模擬。ACROS 的 LUT 處理色調；顆粒等空間效果需要 App 另外實作。

## 2. 免費下載不代表已確認開源授權

本次檢查下載頁、壓縮包檔名、LUT 標頭及包內三份不同系列 PDF 的全文，未找到明確允許第三方 App 修改、商用與重新散布的開源授權。另三份 PDF 位於 65Grid，未逐份閱讀。不能因此斷言官方禁止所有用途，也不能宣稱取得了 App 內建散布權。

下載頁連到的[富士網站使用條款](https://global.fujifilm.com/en/terms)載有個人非商業下載及修改限制，並保留另行明示許可。這是網站通用條款，不能取代 LUT 專屬授權；目前證據不足以把官方 LUT 當成可自由重包的開源素材。

建議：

1. LUT 引擎與自製素材可獨立開發；內建素材逐項記錄來源與授權。
2. 支援使用者從檔案匯入 `.cube`，保留官方下載頁連結；匯入功能本身不解決素材使用權問題。
3. 若要內建、轉換後散布或收費提供富士 LUT，先取得涵蓋這些用途的明確許可。

另有第三方 [pyrocat101/Fujifilm-LUTs](https://github.com/pyrocat101/Fujifilm-LUTs)研究不同色彩空間的轉換，但不是富士官方發布；本次查看的根目錄與 README 未見明確 LICENSE，也不是可直接裝入一般 Android SDR 預覽的現成方案。可參考其方法，不把它當成已確認授權的素材庫。

## 3. 最大的技術問題是輸入色彩

包內 LUT 標頭明示輸入是 F-Log2 + F-Gamut，或 F-Log2C + F-GamutC；輸出色域標示 ITU-R BT.709。一般手機預覽／JPEG 已經過手機 ISP 處理，不是富士 Log 訊號。直接把像素送入上述 LUT，可能產生不正確的對比、曝光與膚色。

不要混淆三個概念：RGB 色域、傳遞函數（gamma／Log）、場景亮度與已渲染的顯示亮度。僅把 sRGB 解 gamma，再編成 F-Log2，無法還原手機已做過的局部 HDR、降噪、銳化與 tone mapping。

建議提供兩種明確模式：

| 模式 | 設計 | 適用情境 |
| --- | --- | --- |
| 一般 SDR LUT | 將手機影像正規化到已宣告的 sRGB／Rec.709 編碼，套用相符的 creative LUT，再轉到輸出空間 | 第一版日常拍照主流程 |
| 富士 Log LUT 適配 | 加入近似的顯示至場景映射、曝光標定、F-Gamut／F-GamutC 轉換與對應 Log 編碼，再套官方 LUT | 可調整、需校色的實驗模式 |

`.cube` 並不可靠地描述完整輸入／輸出色彩空間。匯入時需附加 metadata；不能單靠副檔名自動判定 sRGB、Log 或線性資料。標頭可提示，仍需使用者確認或已知來源設定。

富士白皮書建議監看 Rec.709、D65、gamma 2.2，但這不等於證實每個檔案的完整輸出傳遞函數。建立轉換時需以灰階坡度、色卡與參考軟體核對。sRGB 與 Rec.709 的原色相同，傳遞函數及使用情境仍需區分。

以下是本研究的工程推論：手機適配能做出有用的風格近似，不能補回已裁切的高光，也不保證和富士原機逐像素一致。初版不接受未經轉換的 HDR／Display P3 圖片進 SDR LUT 管線。

## 4. 建議 Android 架構

| 部分 | 選擇與理由 |
| --- | --- |
| 平台 | Kotlin，建議第一版 minSdk 29（Android 10）；屬產品取捨，不是 LUT 的必須條件 |
| 介面 | Jetpack Compose + Material 3；相機主畫面、LUT 列表及設定，適應直橫向與可調視窗 |
| 相機 | CameraX，官方發布頁目前列核心套件穩定版 1.6.2；開工再確認 Compose BOM 與套件相容性 |
| 即時濾鏡 | 自訂 CameraEffect + SurfaceProcessor，以 OpenGL ES／EGL 處理輸入與輸出 Surface |
| LUT 儲存 | `.cube` 解析成 float RGB；支援 33／65 格點，GPU 可用 3D texture 或 2D atlas，使用三線性內插 |
| 拍照輸出 | ImageCapture 取得完整解析度影像，套相同色彩規則與 shader 的離屏版本，再編碼 JPEG |
| 檔案與設定 | 系統文件選擇器匯入 LUT、Photo Picker 匯入照片、MediaStore 存檔、DataStore 保存設定 |

[CameraX 發布頁](https://developer.android.com/jetpack/androidx/releases/camera)是版本依據。[SurfaceProcessor](https://developer.android.com/reference/androidx/camera/core/SurfaceProcessor)官方建議使用 OpenGL 或 Vulkan；這支持採 GPU 管線，尚不代表任何手機都能達到目標幀率。

重要 API 限制：[CameraEffect](https://developer.android.com/reference/androidx/camera/core/CameraEffect)列出的 SurfaceProcessor targets 包括 PREVIEW 及包含 VIDEO_CAPTURE 的組合，並未列 PREVIEW | IMAGE_CAPTURE 的兩者組合。ImageProcessor 則只能 target IMAGE_CAPTURE。第一版使用預覽 GPU 效果與獨立照片後處理，避免為拍照硬綁不需要的錄影串流。高解析度照片不是截取預覽畫面。[ImageCapture 文件](https://developer.android.com/media/camera/camerax/take-photo)提供記憶體與存檔兩種拍攝方式。

Media3 可作替代評估，但不直接作第一版的預設：官方 [ColorLut](https://developer.android.com/reference/androidx/media3/effect/ColorLut)處理的 SDR shader 色彩為線性 BT.709；[SingleColorLut](https://developer.android.com/reference/androidx/media3/effect/SingleColorLut)標為 UnstableApi，內建建立介面使用 ARGB_8888。不能把 F-Log2 編碼表直接視為可互換 LUT，亦需評估量化及高解析度輸出需求。

## 5. 預覽與照片必須共用的規則

```text
相機預覽 → Surface／GPU → 輸入正規化 → 色彩適配 → LUT → 輸出轉換 → 螢幕
完整照片 → 解碼／GPU    → 輸入正規化 → 色彩適配 → LUT → 輸出轉換 → JPEG／相簿
```

兩條路共用 LUT、曝光／強度設定與色彩 metadata，但輸入正規化可因預覽與 JPEG 編碼不同而不同。按快門時固定當下 LUT 及參數，避免背景處理期間換濾鏡影響已拍照片。強度混合必須在同一輸出色彩空間中進行，不能把 Log 原圖直接混入 sRGB 成品。

實作細節：

- LUT 匯入檢查維度、資料筆數 N³、有限數值、DOMAIN_MIN／MAX 與取樣索引順序；第一版遇到不支援的 1D 或混合 shaper 檔明確報錯。
- 匯入後複製到 App 私有目錄，計算內容雜湊；背景解析，上傳 GPU 後才啟用，避免切換卡頓。
- 不為每個預覽幀配置 Bitmap，不在 UI 執行緒逐像素處理。
- 拍照輸出保留時間、方向及可用的 EXIF；避免重複旋轉、錯誤鏡像。位置資訊需獨立設定。
- 預覽裁切與存檔比例採明確規則；使用相同 ViewPort 或顯示完整成品範圍。
- 高解析度輸出按 GPU texture 上限與 RAM 決定分塊處理。33³ RGBA16F 表約 281 KiB，65³ 約 2.10 MiB，僅為表本身計算，不包含相片與 GPU 開銷。

## 6. 第一版範圍

必要功能：前後相機、點擊對焦、曝光補償、裝置支援時的縮放／閃光燈、格線、`.cube` 匯入管理、即時濾鏡選擇、0–100% 強度、完整解析度拍照、原圖選存、相簿儲存，以及匯入一張現有照片套用 LUT。

補充手動模式研究：建議加入依鏡頭能力開放的快門、ISO、白平衡，以及可變光圈控制；細節見第 8 節。

第一版全程離線即可完成，不需要帳號、AI、資料庫服務或雲端。色彩實驗先挑 CLASSIC CHROME、ETERNA、ACROS 三種檢查膚色、灰階與高光；素材使用仍依授權處理。

後續再考慮顆粒、暈光、配方、影片、RAW 與 HDR。3D LUT 是依單一像素 RGB 查表，顆粒與暈光另需效果層。[Camera Extensions](https://developer.android.com/media/camera/camera-extensions)依裝置與模式支援，不能承諾所有手機都有原廠夜景或 HDR 品質。

## 7. 開發順序與驗收

1. **靜態色彩原型**：先做匯入照片與 LUT、identity LUT、灰階／色卡；用 CPU 三線性參考核對 GPU。證實輸入空間正確，再研究富士適配。
2. **即時相機原型**：CameraX + SurfaceProcessor + Compose，處理旋轉、前鏡頭、生命週期與 GPU 重建。
3. **完整照片輸出**：快門參數快照、離屏渲染、JPEG／EXIF／MediaStore、原圖選存；處理分塊、失敗與重試。
4. **真機品質**：至少一台 Pixel、一台 Samsung、一台中階手機；白天、室內混光、暗處、前後鏡頭；驗證支援能力與熱降頻。

初步目標是 720p／1080p 預覽穩定 30fps；33ms 是一個幀週期，不能直接當成相機至顯示的總延遲。12MP 處理時間、峰值記憶體、連拍恢復與連續使用 10 分鐘的熱表現需真機量測，本次沒有實測數據。

驗收時不只比預覽和照片肉眼是否像：先檢查 identity 的中性色與 gamma，固定同一圖像來源比較 GPU／CPU，接著比較同場景預覽與成品差異。手機 ISP 的兩條輸出可能不同，需記錄差異而不能宣稱共用 shader 即完全一致。

## 8. 光圈、快門、曝光、白平衡與 ISO

### 可行性與硬體限制

可以加入專業拍攝模式，但功能及範圍要按每個 camera ID／實際鏡頭查詢，不能預設主鏡頭、超廣角、望遠與前鏡頭能力相同。原廠相機具有某項控制，也不等於第三方 App 的公開 API 一定可使用。

| 參數 | 可行性 | 產品行為 |
| --- | --- | --- |
| 光圈 | 僅可控制公開可變光圈鏡頭 | 依支援的 f 值列出選項；固定光圈只顯示數值，未回報則顯示未知 |
| 快門 | 支援手動感光元件控制時可調 | Auto／手動，以 1/125s、1/30s 等顯示，範圍由鏡頭回報 |
| ISO | 支援手動感光元件控制時可調 | Auto／手動，限制在鏡頭允許範圍內 |
| 曝光補償 EV | 自動曝光有效且裝置支援補償時可調 | 根據裝置步進與範圍，不能硬編碼所有手機都是 ±3EV |
| 白平衡 | 預設模式、鎖定及手動能力各別查詢 | Auto、日光等可用預設；支援時增加 K 色溫與色偏 |

能力來源：[CameraCharacteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)。檢查 `REQUEST_AVAILABLE_CAPABILITIES`、可用 request／result keys、ISO／快門範圍、AWB 模式、曝光補償步進及 `LENS_INFO_AVAILABLE_APERTURES`。光圈清單只有一個值表示固定光圈；多個值亦需驗證 request 可寫與 result 是否反映變化。不要把切換不同鏡頭當成調整同一鏡頭光圈。

### 控制規則

[CaptureRequest](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)提供快門 `SENSOR_EXPOSURE_TIME`、ISO `SENSOR_SENSITIVITY` 與 `LENS_APERTURE`。全手動使用 AE OFF；EV 補償在 AE OFF 時不起作用。手動白平衡可用 AWB OFF + gains／transform，但色溫映射需校正，不能一套常數通用所有感光元件。

[Android 16／API 36 的 CCT 模式](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#COLOR_CORRECTION_MODE_CCT)新增直接色溫與色偏控制，必須 AWB OFF，且裝置宣告支援 CCT。API 36 也有快門／ISO 優先模式；依 `CONTROL_AE_AVAILABLE_PRIORITY_MODES` 判定，不能只看作業系統版本。

這裡的曝光須分為兩項：

- **拍攝 EV**：調整自動測光目標，會影響實際快門／ISO 等拍攝決策。CameraX 可用 [setExposureCompensationIndex](https://developer.android.com/reference/androidx/camera/core/CameraControl#setExposureCompensationIndex(int))；顯示 EV = index × 裝置 step。
- **影像亮度**：GPU 在 LUT 前的亮度修正，是調色設定；不能改變已拍下的動態模糊、景深或找回裁切的高光。介面以獨立名稱顯示。

建議第一版只提供以下曝光組合，避免 Auto 開關產生未實作的半自動行為：

1. Auto：快門與 ISO 自動，支援時可調 EV；白平衡可獨立選擇。
2. M：快門與 ISO 手動，AE OFF；拍攝 EV 停用，保留獨立影像亮度調整。
3. 支援 API 36 優先模式時，增加快門優先或 ISO 優先。舊系統若要半自動，需自行建立測光迴路，屬後續工程。

可變光圈的 M 模式才開放 f 值設定；Auto 模式需處理 AE 覆寫。軟體散景是另外的景深模擬功能，不能用它替代物理光圈。

### 與 CameraX／LUT 的搭配

建議先保留 CameraX，以 Camera2 interop 實作手動控制，降低相機生命週期與機型相容性成本；把相機控制抽象成獨立介面，若真機證明拍攝要求無法滿足，再評估專業模式採直接 Camera2。自訂 LUT shader 可以繼續沿用，直接 Camera2 則要自行管理輸出 Surface 與 capture session。

目前官方 [Camera2CameraControl](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2CameraControl)文件列出 interop 選項會覆蓋 CameraX 控制，並送到 repeating／single capture requests，因此必須驗證拍照、閃光燈與自動控制是否衝突。[Camera2Interop.Extender](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender)標為 ExperimentalCamera2Interop；文件中新的替代擴充介面及舊方法棄用標記屬 1.7.0-alpha03，不能直接視為已選定的穩定版 1.6.2 API。實作時按實際依賴版本選擇並集中 opt-in。

本研究建議的處理順序：拍攝參數／硬體白平衡 → 相機輸出 → 輸入正規化 → 可選的調色色溫／亮度 → LUT → 輸出。若只能提供軟體色溫調整，清楚標為調色；硬體已完成白平衡時，避免重複套同一校正。

### 真機驗收與預覽限制

- UI 分別保存「要求值」與 [CaptureResult](https://developer.android.com/reference/android/hardware/camera2/CaptureResult)的實際值；裝置可能量化或覆寫設定。以結果確認 ISO、快門、光圈與白平衡，不以滑桿移動表示已成功。
- 從 Auto 進入 M，先穩定／鎖定 AE、AWB 及對焦，再讀取結果作為起始值；不要把所有 AUTO 控制一律關掉。返回 Auto 清除手動覆寫。
- 按快門前確認已套用目標設定，成品關聯該次 result 與 LUT 參數快照，並保存可用 EXIF。
- 慢快門會限制即時更新速度。例如曝光 1/4 秒，若預覽也用相同曝光，理論上限約 4fps，還沒計入其他開銷；不能維持原先 30fps 目標。選擇真實曝光預覽或另設構圖預覽時，要標明差異。
- 切換鏡頭重新檢查範圍，調整超界值並提示；不支援的控制顯示原因。額外測試手動模式與閃光燈、Extensions、連拍、方向切換及背景恢復。

本節完成的是 API 與設計可行性研究，尚未取得使用者手機的實際能力報告，也未驗證任何特定機型的可變光圈或手動色溫。

## 尚待確認

- 使用者所指的「開源 LUT」是否為這個官方包，或另一個具明確授權的專案。
- 富士對 App 內建、適配轉換與商業散布是否另有書面授權。
- 首要測試手機型號，以及希望做日常風格相機或精準富士色彩研究工具。
- 預覽資料空間、成品 transfer function、GPU 格式支援及 ISP 差異，開發時以真機驗證。

後續若另做 Vercel 官網或後端，建議安裝 `npm i -g vercel`，便於 `vercel env pull`、`vercel deploy` 與 `vercel logs`；原生相機核心不依賴它。
