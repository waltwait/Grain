# Grain 0.4.4 底片 LUT

2026-10-02。新增六款原創 LUT 及五款既有 Kodak 社群模擬，沿用同一個 GPU 查表、照片處理與錄影流程。手機不在手邊，本次沒有安裝或執行裝置測試。

## Grain Originals v1

| 名稱 | 調色方向 |
| --- | --- |
| Daylight | 自然日系，柔和對比、稍收斂綠色 |
| Warm Portrait | 柔和暖調、保護橙色膚色區段 |
| Chrome Street | 低飽和、橄欖綠與冷色陰影 |
| Golden Hour | 金色亮部、暖色陰影 |
| Night Cinema | 藍綠陰影、收斂高飽和色彩 |
| Silver | 單色、較明顯對比、保留灰階 |

這些是原創 creative looks，沒有套用第三方 LUT，也不主張量測重現特定品牌底片。輸入／輸出為 sRGB SDR，預設沿用既有強度設定，可調 0–100%。

使用單調的明暗曲線、平滑色相區段、橙色範圍的飽和度與色偏保護，以及柔和 RGB 色域壓縮。膚色保護針對色相區段，沒有偵測人臉。已裁切的高光無法恢復，沒有顆粒或光暈。

`scripts/generate_grain_originals.py` 是調色來源，離線生成 33³ RGB float32 查表及可匯出的 `.cube`。預先生成資料，在 App 啟動時背景載入，不在預覽／錄影的每幀計算調色公式。六款資料本體約 2.47 MiB；實機載入耗時尚未量測。

比較色卡：`output/design/grain-originals-v044-preview.png`。獨立 CUBE 濾鏡包：`output/Grain-Originals-v1-sRGB-LUTs.zip`。色卡以天空漸層、暖膚色區段、植物／色塊與灰階顯示差異，不是實拍照片。

## 現成 Kodak 模擬

| 名稱 | 來源檔案 |
| --- | --- |
| Portra 160 | `negative_new/kodak_portra_160.png` |
| Portra 400 | `negative_new/kodak_portra_400.png` |
| Portra 800 | `negative_new/kodak_portra_800.png` |
| Ektachrome 100 VS | `colorslide/kodak_ektachrome_100_vs.png` |
| Tri-X 400 | `bw/kodak_tri-x_400.png` |

作者為 Pat David，採用 [Natron HaldCLUT](https://github.com/NatronGitHub/clut) 的既有資料，保留其 [CC BY-SA 4.0 授權說明](https://github.com/NatronGitHub/clut/blob/master/README.md)。作者將其定位為創作近似模擬，並非 Kodak 官方 LUT 或精確底片重現：[作者原始介紹](https://patdavid.net/2013/08/film-emulation-presets-in-gmic-gimp/)。

固定來源 commit 為 `af7b50d4caf6244fb6895a647f5b6a84efe7931a`（2016-09-16）。每個 PNG 的 Git blob SHA-1 都與來源清單一致，另記錄 SHA-256。原始圖放在 `third_party/luts/natron/`，授權、作者、轉換及個別來源網址隨 App assets 與 CUBE 濾鏡包一起保留。

來源為 512×512 RGB Hald，等於 64³ RGB 格點。`scripts/prepare_kodak_luts.py` 保留所有原始 8-bit 色彩數值與順序，直接重包成二進位查表；沒有降採樣、額外調色或色彩轉換。載入時以 `byte / 255f` 轉成浮點數。輸入／輸出按 [RawTherapee film-simulation 的 sRGB 慣例](https://rawpedia.rawtherapee.com/Film_Simulation) 使用，手機 ISP 與影片轉移曲線的實際觀感仍待驗證。

五款包裝資料合計約 3.75 MiB，載入後 RGB float32 本體約 15 MiB。每幀仍使用同一 shader；64³ 查表的 GPU 上傳成本、快取行為及持續錄影速度需真機確認。

選單將 Kodak 項目排在 Originals 前面，附短風格說明、Pat David／CC BY-SA 4.0 標示及來源／授權連結。沿用原有富士、示範與匯入 LUT 的 ID，保留既有選取設定與強度。

比較色卡：`output/design/kodak-emulations-v044-preview.png`。獨立濾鏡包：`output/Grain-Kodak-Emulations-v1-sRGB-LUTs.zip`，內含五款 64³ `.cube`、作者說明與來源授權。

## 其他已研究的 Kodak 路線

- [Juan Melara 的 Kodak 2383／2393](https://juanmelara.com.au/blog/print-film-emulation-luts-for-download)：電影印片模擬，作者要求 Log／Rec.709 輸入，不能直接視為手機 sRGB 調色；本版沒有內建。
- [spektrafilm](https://github.com/andreavolpato/spektrafilm)：使用底片資料與光譜模型，可烘焙指定輸入／輸出的 LUT，適合後續研究。需固定模型版本、處理色彩輸入、確認作者最新授權及建立比較流程；本版沒有使用其程式或素材。
- [YahiaAngelo/Film-Luts](https://github.com/YahiaAngelo/Film-Luts)：有現成 CUBE 可探索，但 README 同時聲明維護者不擁有 LUT；此次改採有作者與授權說明的 Natron 素材。

## 驗證

- 新增七項 Originals 測試：風格差異、暖膚色通道與色相、灰階單調性／白黑端點、天空連續性、數值範圍、Silver 單色與格式校驗。
- 新增三項 packed-loader 測試：8-bit 正確通道與內插、串流關閉、損壞／超範圍／截斷／尾端資料拒絕；原有 Originals 測試同時覆蓋 float32 格式。
- 新增兩項 Kodak 測試：3,932,160 個來源色彩數值與 Android 查表逐值一致、Tri-X 有色輸入仍為單色。
- 65 項 JVM 測試通過，0 失敗、錯誤或跳過。另有兩項 Python Hald 轉換測試通過，確認獨立 identity fixture 的 RGB／格點順序與無效圖像拒絕。
- 兩個生成工具的 `--check` 通過；來源 blob 校驗、資產 manifest SHA-256 校驗通過。十一款 CUBE 匯出共 4,579,026 個 float32 數值與 App 資料逐位元一致。
- Debug、AndroidTest、R8 精簡後未簽章 release 建置通過；lint 0 錯誤、21 警告，與 0.4.3 相同。
- 新增裝置 shader 測試覆蓋十一款的膚色、灰階與高飽和色彩，對照 CPU 三線性內插；只編譯，未執行。

正式確認畫質仍需實拍人像、植物、晴天／陰天、混合照明與霓虹夜景，再比較觀景窗、照片和影片。色卡與數值校驗可抓出格式、通道和色階問題，不能證明特定手機的實拍畫質已提升。

APK 為 `output/Grain-0.4.4-personal-fuji-debug.apk`（`tw.luma.camera`，versionCode 14）。私人測試包保留十款官方富士 LUT，新增五款 Kodak 社群模擬與六款 Originals；公開可建置的 release 不包含官方富士素材。APK 和官方富士 LUT 不提交 Git。

個人 APK 為 53,409,108 bytes（約 50.9 MiB），SHA-256 為 `1a0bf3cf4d61d5fe1cd286f5cf66611a5b006343066acb9962988c2202753dac`。簽章有效且與 0.4.3 相同；APK 內十一款新資產與來源檔逐 byte 相同，十款富士檔也與 0.4.3 相同。最後一次完整檢查完成於 1 分 19 秒；含主程式改動的前次建置為 4 分 43 秒。
