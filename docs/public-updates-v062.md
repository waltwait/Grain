# Grain 0.6.2 公開更新來源

使用者於 2026-10-03 要求將 `waltwait/Grain` 改為 public，讓 App 能直接下載更新。本次沿用「設定 → 檢查更新 → 下載新版 → 安裝」，公開設定只解決版本資訊及 APK 的匿名存取；目前安裝仍開啟 Android 系統安裝畫面。

狀態：儲存庫已改為 public，匿名 Release API 可取得既有最新版 0.6.1。0.6.2 APK 已驗證並上傳為草稿；公開發佈被自動核准審查拒絕，因尚未確認十款官方富士素材的公開再散布授權及使用者對此素材包的明確授權。目前版本資訊與下載頁只指向已發佈的 0.6.1；不將 0.6.2 草稿改用其他方式公開。

## 固定來源

| 用途 | 網址 |
| --- | --- |
| 個人富士通道 | `https://raw.githubusercontent.com/waltwait/Grain/main/updates/personal-fuji/latest.json` |
| 一般 release 通道 | `https://raw.githubusercontent.com/waltwait/Grain/main/updates/release/latest.json` |
| 下載頁 | `https://waltwait.github.io/Grain/` |
| 目前公開 APK | `https://github.com/waltwait/Grain/releases/download/v0.6.1/Grain-0.6.1-personal-fuji.apk` |

App 的預設更新網址依 build type 選擇通道；`personal` 保留十款富士 LUT，並沿用正式簽章。仍可用 `-PgrainUpdateUrl` 指定自架來源。一般通道不切換成個人包，個人通道不切換成缺少富士素材的包。

兩份 JSON 由實際簽章 APK 產生，含實際版本號、minSdk、素材數量、大小、SHA-256、簽章及不可變的版本 APK 下載網址。APK 不提交 Git，每次 Release 只提供一個 APK 附件。原始碼保留既有富士素材來源與使用範圍說明；public 設定不等於取得第三方素材的開源授權。

公開前核對了 826 個 Git objects：沒有追蹤簽章／keystore／本機設定路徑，沒有找到常見私鑰與 GitHub／AWS 權杖格式，沒有官方富士原始資產進入 Git 歷史。這是具體項目的檢查，不代表完整安全審計。既有 Release 附件會隨儲存庫公開，沒有刪除或替換歷史 APK。

## 下載頁更新

`.github/workflows/pages.yml` 只把 `website/` 的三個網頁檔案及版本 JSON 部署到 Pages，不上傳整個儲存庫、APK 或本機簽章。主頁讀取個人通道，`release/latest.json` 保留一般通道。更新 `updates/` 後會觸發網站重新部署；App 直接讀取 GitHub raw JSON，不依賴 Pages 部署是否完成。

後續發布流程：建置同簽章 APK → 核對十款富士與簽章 → 產生版本 JSON → 上傳版本 APK → 更新對應 `updates/<channel>/latest.json` → 提交並推送。JSON 需與 Release 實際附件一致；保留舊版本 URL，不能把高版號資訊指向不同 APK。

## 安裝界線

第一次使用更新模組仍需手動下載並覆蓋安裝 0.6.2；舊 0.6.1 沒有更新頁。後續可以在 App 內檢查、下載並按安裝。首次使用 Grain 作為安裝來源時，要允許來源安裝。

Android 12 以上提供符合條件的自我更新 API，但仍要求對應權限及版本條件，並須處理系統要求確認的結果。本版沿用明確按安裝的系統畫面，沒有實作背景靜默安裝，也沒有因 public 就擴大安裝權限。依據：[PackageInstaller 官方文件](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))。

## 驗證

正式網址下的個人與一般 APK、裝置測試編譯成功（1 分 16 秒）；143 項 JVM、4 項網站測試通過；lintPersonal 0 錯誤、22 警告。十款富士原始 bytes 與其餘 23 個 LUT／native library 項目保持一致；16KB ZIP alignment 通過。metadata 產生器驗證 APK 為非 debug、code 25、既有正式簽章，並以成品產生大小與雜湊。

正式網址成品 `output/Grain-0.6.2.apk`，14,000,346 bytes，SHA-256 `f0d112f541b29ec368d627c79ced240215dc7a730838744d7bff9830d9c19a20`。與先前 `.invalid` 測試網址包的雜湊不同，測試包不發布。

待核對固定 JSON、既有公開 APK 的匿名下載與 Pages 部署。0.6.2 草稿仍待使用者確認及核准，不表示已公開發布。Samsung S24 Android 16 的安裝授權、取消及覆蓋更新仍待真機；目前無手機，不宣稱已通過。
