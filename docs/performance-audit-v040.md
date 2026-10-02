# Grain 0.4.0 逐項效能檢查

2026-10-02。基準為 main 的 8c4b0d3（0.3.0）。此回合沒有連接／操作手機；區分已量測的 JVM 配置量、可由程式碼計數的工作量，以及尚未量測的手機表現。

## 檢查結果

| 區塊 | 找到的成本／現況 | 本次處理與仍需量測項目 |
| --- | --- | --- |
| 觀景窗 | GPU thread 處理 SurfaceTexture，預覽與錄影各 draw／swap；沒有每幀 CPU Bitmap 轉換 | 保留此流程，加入取得影格、draw、preview／video present 追蹤；輸出等待與裝置 GPU 負載待實測 |
| LUT 載入 | 每個資料行重新建立 Regex／matcher，再切欄位 | ASCII 空白分欄改用單次掃描；既有數值、domain、註解、BOM、65 格點檢查保留；配置量見下表 |
| LUT 匯入 | 完整檔案留在 ByteArrayOutputStream，再複製成 ByteArray 和 UTF-8 String | 8 KiB buffer 串流寫暫存檔並同時計算 SHA-256，直接由 reader 解析；通過解析後才 rename，失敗清暫存檔；保留原始 bytes 與 24 MiB 上限 |
| GPU 每幀設定 | 每 draw 查兩個 attribute、設定兩個固定 sampler、重送九個濾鏡 uniform | attribute 與 sampler 在建立程式時設定；九個 filter uniform 僅在設定改變時重送；輸出 transform 仍每 draw 設定 |
| 原色／零強度 | 亮度為零仍每像素做 sRGB decode／encode；零強度仍做 Log 與 LUT lookup | 零亮度跳過往返轉換，原色／零強度跳過 Log／LUT；零強度且零亮度照片直接回傳原 bitmap |
| LUT texture | 已有 LUT reference 快取，原本就不會每幀上傳 | 保留；切換 LUT 的 atlas 配置及上傳加入 trace；未誤判成每幀重載 |
| 拍照分塊 | 每塊建立直接 ByteBuffer 與 IntArray，逐像素讀四次 byte | 共用最大實際分塊大小的一組 buffer／array，用 IntBuffer 批次讀回，再原地轉 RGBA→ARGB；保留方向、色彩及邊界 |
| 拍照儲存 | 解碼、濾鏡、JPEG95、EXIF、MediaStore 已在 IO dispatcher；bitmap 與 GL 資源有 finally 清理 | 保留照片品質與 12MP 限制，加入 decode／filter／JPEG／MediaStore trace；GPU readback 同步等待與每張 EGL 初始化尚待量測 |
| 錄影 | CameraX Recorder＋GPU 輸出，沒有逐幀 CPU 編碼 | 保留時間戳與輸出流程，未降解析度或刻意跳幀；encoder 回壓、掉幀、溫度與音畫同步待手機量測 |
| 錄影計時 | Recorder 事件讓整個 CameraScreen 觀察的 state 改變 | 主介面排除錄影時間與實際參數；計時先按秒去重，再格式化；只在 RecordingClock 收集 |
| 實際 ISO／快門／WB | 已將 UI 回報節流至約300ms，但主畫面仍觀察所有資料 | 小型讀值徽章及 WB 滑桿各自收集；保留每影格 latest 供進入手動時取得準確參數 |
| ISO／快門／曝光／白平衡控制格 | 每次畫面更新重建格子清單；量化滑桿可能送相同值 | 記住鏡頭能力對應清單；相同 capture／filter／zoom 保留原 state，避免無用 copy；未捨棄最後手勢值 |
| 變焦／曝光手勢 | 原已有不重綁相機的 fast path | 保留錄影變焦；完整 ISO／WB 設定變更不再重送未變的 zoom；實際連續滑動的流暢度待手機 |
| 點擊對焦動畫 | 已以 graphicsLayer 讀取 Animatable，動畫不重建相機；request key 取消舊動畫 | 保留；單指曝光、多指縮放與對焦的競合待手機測試 |
| 濾鏡選擇／設定面板 | LazyRow 已有 key；背景解析，切換指向已載入 CubeLut | 保留，沒有每次切換重新讀檔；大型使用者 LUT 集合常駐容量仍需實測 |
| 內建相簿 | 原來交給外部 ACTION_VIEW，沒有自己的瀏覽 UI | 改為自製相簿，背景查詢、限並行縮圖、大圖按需解碼、LRU 快取、單播放器、離開釋放、暫停相機，詳見相簿文件 |

## 可確認的成本降低

LUT 使用同一桌面 JDK17、JUnit fixture：33³／65³，所有資料行都是 `0.1 0.2 0.3`，reader 來自已建好的 String；每尺寸暖身3次後量5次，記錄中位數。以 JDK ThreadMXBean 的執行緒累計 allocated bytes 差值量測。涵蓋解析與建構的暫時配置，不是保留 heap、GC 次數、官方檔案完整載入時間或 Android 幀率。

| 格點 | 0.3.0 暫時配置量 | 優化後暫時配置量 | 降幅 |
| --- | ---: | ---: | ---: |
| 33³ | 41,854,816 bytes | 15,691,952 bytes | 約62.5% |
| 65³ | 319,729,648 bytes | 119,801,920 bytes | 約62.5% |

原始 JVM 基準與優化後記錄保存在 `output/performance/`。耗時受 JIT、GC 與其他建置工作影響：不同輪的 65³ 中位數有明顯波動，沒有把桌面耗時宣稱為 Android 效能提升，也沒有用耗時門檻使測試不穩定。

12MP 例子（4000×3000）：舊版所有分塊的 readback ByteBuffer＋IntArray 累計 payload 配置為 96,000,000 bytes；新版固定配置一組1024×1024 staging，payload 為8,388,608 bytes（8MiB），理論累計配置減少91.3%。這是程式碼推導，不是整張照片的峰值記憶體或裝置量測；輸入／輸出 bitmap、分塊 bitmap、GPU textures、LUT atlas 仍需空間。

每個設定未改變的 draw，固定 filter／sampler uniform 呼叫由11個降到0個，attribute location 查詢由2個降到0個；transform uniform 與紋理綁定仍保留。此為呼叫數計算，未推定每個呼叫的實際延遲。

## 本次建置驗證

- 45 項 JVM 測試通過，包含既有 LUT／色域／相機控制與新增的讀回、串流匯入、照片縮放測試及配置基準。
- debug App、Android 裝置測試 APK、R8 壓縮 release APK 建置通過。裝置測試只編譯，未執行。
- lint：0 errors、20 warnings（現有依賴更新與一般建議）；沒有新增效能錯誤。既有裝置測試 rule 有 deprecated API 警告，保留測試排程行為，未在沒有手機時改動測試框架。
- APK：`output/Grain-0.4.0-personal-fuji-debug.apk`；另附 SHA-256。套件 `tw.luma.camera`、versionName 0.4.0、versionCode 10、minSdk29／target37。
- 確認 debug APK 有10個官方富士 CUBE，release 有0個；素材與 APK 不提交 Git。沒有安裝或操作手機。

## 手機回來後的量測

1. 先執行裝置功能測試：錄影＋變焦、曝光手勢、GPU 8項、相簿／照片縮放。0.2.0 曾出現錄影啟动等待及 Compose hierarchy 兩項失敗，原因未證實；本次未宣稱已修好。
2. 相同手機、鏡頭、光線、電量、溫度及設定，比較0.3.0與0.4.0。優先使用 release／profileable 建置；debug 只作診斷，不能作最終速度結論。
3. 冷啟動與10款富士載入；原色、富士、銀影各預覽30秒；錄影邊變焦60秒；相簿快速捲動／連翻照片及影片；連拍10張12MP；持續使用10分鐘。
4. Perfetto 檢視 `Grain.preview.frame/acquire/draw/present`、`Grain.video.present`、`Grain.lut.*`、`Grain.photo.*`、`Grain.gallery.*`；比較 p50／p95／p99、UI frame deadline、影格間隔、GC、CPU scheduling 與記憶體。
5. Trace sections 是 CPU 程式執行、提交與等待區間；不能當作 GPU execution time。GPU timer／實際輸出幀率與 encode drop 必須另量測。
6. 相簿反覆進出及影片前後景測試確認相機／播放器沒有累積；熱機下評估解碼、續航與播放流暢度。

若 trace 指向照片 EGL 初始化、紋理重配置或同步 readback，再評估專用長駐照片 GL thread、texture storage 重用或 PBO；若指向錄影 swap 等待，評估輸出策略。這些跨 GPU／生命週期的修改先保留，避免沒有實機證據就增加複雜度。

## 依據

- [Compose 效能最佳實務](https://developer.android.com/develop/ui/compose/performance/bestpractices)：減少重複運算與讀取範圍。
- [Android 自訂追蹤事件](https://developer.android.com/topic/performance/tracing/custom-events)：以可閉合的同步 section 標記工作，搭配裝置 trace。
