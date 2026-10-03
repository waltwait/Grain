# 獨立照片濾鏡預覽

先前的照片匯入流程會立即用相機當下的設定輸出照片。現在改為獨立的 Compose 編輯頁：相機下方的「編輯」、相簿的「編輯」可挑選照片，單張照片檢視頁的「編輯」則直接使用目前照片。相機設定內的舊匯入按鈕移除。

選圖後先顯示原圖，可從 FUJIFILM、KODAK、GRAIN 三個英文品牌挑選濾鏡、調整強度，切換原圖比較及更換照片。品牌瀏覽及卡片選擇沿用原有邏輯。編輯使用獨立 ViewModel 與濾鏡強度記憶，不修改相機的 LUT、調色設定或偏好設定。

只有按「儲存」才寫入 MediaStore，使用目前選擇的濾鏡輸出新 JPEG。原始 URI 僅讀取；取消不產生相簿照片。匯入的完整檔案暫存在 App 私有快取，離開或換圖時清理。儲存後按鈕顯示「已儲存」，更新 Grain 相簿與相機縮圖，不跳出位置或成功通知。相同設定不重複存檔；更改設定後可再次輸出。儲存期間停用換圖、調整與返回，避免來源檔案在輸出中被移除。

預覽最多 150 萬像素、長邊最多 2048，讀入時轉成 sRGB 並套用 EXIF 旋轉／鏡像。Compose 頁面透過 AndroidView 承載 GLSurfaceView，沿用 LutRenderer；原始預覽圖只解碼及上傳一次，LUT 只在切換時上傳，強度使用 shader uniform。只在設定或 surface 改變時繪製，請求合併為最新一筆，不做逐次 CPU readback。頁面離開或 App 暫停時停止 GL rendering 並釋放 context。生命週期依 [GLSurfaceView 文件](https://developer.android.com/reference/android/opengl/GLSurfaceView)，Compose 管理 View 的釋放依 [AndroidView 文件](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)。

預覽的 revision 與照片 bitmap 身分一起核對，只有目前選擇的 GPU 預覽完成後才可儲存；原圖比較期間不能儲存。旋轉及視窗尺寸變化保留編輯設定，相機只在相機頁啟用。相簿與編輯頁保留各自的可儲存 UI 狀態，編輯結束返回原本所在功能。

匯入維持 80 MB、50MP 上限。儲存重新讀取來源檔案，沿用 PhotoStorage 的 sRGB、EXIF、JPEG 95 與 **12MP 處理上限**，較大的圖片會下採樣；不是把預覽縮圖直接儲存。JPEG／PNG／WebP／HEIC 的實機解碼與色彩仍需驗證，HDR／廣色域完整保留不在此版本範圍。

## 驗證

- 全部 126 項 JVM 測試通過，新增 12 項涵蓋預覽更新與儲存 gating、原圖比較、重複儲存、完整畫面比例、大小／像素限制與可取消複製。
- Lint 0 錯誤、20 警告。GL View 的 ViewConstructor 警告局部註記，因其由 Compose 直接提供 callbacks，不從 XML inflate。
- AndroidTest、R8 Release APK 與 AAB 建置成功，耗時 52 秒；R8 mapping 確認保留 PhotoEditorViewModel 的 Application／SavedStateHandle 建構子。
- 新增裝置測試：預覽與比較不產生相簿檔案、旋轉後保留強度、相機濾鏡不變、原圖 bytes 不變、以來源尺寸及 EXIF 方向輸出、輸出顏色符合所選 LUT 與強度；另加入 GPU viewport 留黑邊與上下方向測試。**本次只編譯，沒有手機執行結果**。
- 成品與簽章、LUT、16 KB 對齊檢查另見 [Release 準備紀錄](release-publishing-v050.md)。手機上的預覽效能、觸控、原圖比較、背景恢復與儲存仍需實測。
