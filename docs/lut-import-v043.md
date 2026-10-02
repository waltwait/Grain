# Grain 0.4.3 LUT 匯入效能

2026-10-02。針對 LUT 匯入的本機處理成本優化，保留 CUBE 的數值、原始檔案與格式檢查。

## 改動

0.4.2 先複製來源檔案並計算 SHA-256，再讀回暫存檔解析。0.4.3 使用同一次來源讀取，同步寫入暫存檔、計算 SHA-256 與解析；讀到 EOF 且校驗成功後才關檔並改名發布。失敗仍清理暫存檔，不加入濾鏡清單。

RGB 資料直接掃描三個數值，避免每行建立 token 清單與常見小數的三份字串。最多九個數字的普通十進位使用精確整數與 Double 除法；接近 Float 捨入中點時回到標準解析。科學記號、長小數、十六進位等原本可接受的數字同樣使用標準解析，保留浮點位元與負零。

資料行由固定 8192 字元緩衝區讀取，跨區塊時才組合字串；超過行長限制先拒絕，避免建立任意長度資料行。支援 CR、LF、CRLF、跨區塊分隔及檔尾沒有換行。

保留 24 MiB bytes 上限、4096 字元行長拒絕、2–65 格點、資料數量、finite 數值與 domain 校驗。匯入後一次發布 LUT 清單與選取狀態，沿用背景 IO 與既有 GPU 上傳。

## 桌面比較

從 0.4.2 commit `3e9dad65c1b7f9f6b9e8a547044fdb917fb3f380` 提取未修改的解析與複製程式，以另一 namespace 與新版本在同一 JDK17 測試程序交替執行。合成檔案包含 `LUT_3D_SIZE`，以及重複的 `0.123456 0.456789 0.789123` RGB 資料行。

來源 bytes 預先建立；舊版執行記憶體來源讀取、複製到同一暫存檔、SHA-256、關檔、讀回並解析；新版執行來源讀取、複製、SHA-256、解析與關檔。每尺寸各暖身三次，再交替量七次，取中位數。暫時配置使用 JDK ThreadMXBean 的本執行緒累計 allocated bytes 差值。

| 格點 | 0.4.2 耗時 | 0.4.3 耗時 | 耗時減少 | 舊／新暫時配置 |
| --- | ---: | ---: | ---: | ---: |
| 33³ | 25.87 ms | 12.16 ms | 53.0% | 17,184,064／3,065,608 bytes |
| 65³ | 183.34 ms | 82.38 ms | 55.1% | 130,934,584／23,250,432 bytes |

配置量減少約 82%。數字包含測試 helper 的固定成本，不代表保留 heap 或 App 峰值記憶體；耗時受 JIT、GC 與本機磁碟快取影響。未包含 Android 選檔器、ContentProvider／雲端下載、偏好設定、介面更新或 GPU 上傳，不能直接視為手機速度。

原始結果：`output/performance/lut-import-v043-comparison.xml`。原有短數字 parser fixture 的前後記錄另保存在 `lut-parser-v042-baseline.xml` 與 `lut-parser-v043-first.xml`；兩種 fixture 的數字不可混合比較。

## 精度與邊界驗證

- 十款本機富士 LUT 與 0.4.2 對照，1,078,110 個 Float 值逐位元一致，維度、名稱及推定輸入 encoding 一致。素材未加入 Git。
- 新增 15,000 組固定 seed 十進位樣本，對照標準 Float 解析；另測負零、捨入中點、長小數、subnormal 科學記號與十六進位數。
- 驗證跨 Reader 區塊的 CRLF、沒有檔尾換行，以及過長資料行會提前拒絕。
- 串流匯入驗證 UTF-8／BOM 原始 bytes 與 SHA-256、分段讀取、大小上限、損壞檔案、儲存失敗與 stream 所有權。

## 建置與實機範圍

執行 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`，建置成功，耗時 1 分 31 秒。

- 53 項 JVM 測試通過，0 失敗／錯誤／跳過。
- Debug、裝置測試與 R8 精簡後的未簽章 release APK 建置通過；裝置測試只編譯。
- Lint 0 錯誤、21 警告；比 0.4.2 多一項 SharedPreferences.edit 的 UseKtx 建議，沒有新增效能或正確性錯誤。
- APK 為 0.4.3／13、`tw.luma.camera`；debug 含十款富士 LUT，release 不含官方素材。
- 個人 APK 簽章有效且與 0.4.2 相同，約 46.0 MiB，附 SHA-256。

實際相機、匯入速度、GC／預覽頓挫及 GPU 套用仍待手機確認；此次沒有連接或安裝手機。

匯入 trace 改為 `Grain.lut.importReadParse`，涵蓋交錯的來源讀取、寫入、校驗及解析。`Grain.lut.upload` 沿用，可在手機上區分本機匯入與 GPU 上傳所需時間。

個人測試版為 `output/Grain-0.4.3-personal-fuji-debug.apk`，含十款富士 LUT，沿用既有 debug 簽章；依使用者要求提供私人 GitHub Release 下載。官方素材與 APK 不提交 Git。
