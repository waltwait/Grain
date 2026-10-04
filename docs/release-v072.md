# Grain 0.7.2

開 App 時在背景安靜地檢查一次更新，更新頁文字變少，全 App 固定使用 Grain 經典字體。版本 0.7.2／versionCode 35，沿用正式簽章、套件與十款富士 LUT。

## 變更

- **開 App 自動檢查更新：** 每次啟動在背景讀一次版本資訊，不顯示轉圈、不擋相機、不自動下載 APK；旋轉螢幕不會重複檢查。檢查失敗保持安靜，進更新頁才會顯示錯誤。有新版時沒有彈窗和通知，只在相機畫面「更多設定」圖示右上角出現一顆小金點（螢幕閱讀器讀到「有新版」），設定裡的按鈕文字從「檢查更新」變成「有新版 x.y.z」。已是最新版時沒有任何變化。
- **更新頁文字變少：** 說明文字只在有新版時顯示，最多 2 行，超出以省略號截斷；「已是最新版」時不顯示說明。0.7.1 起 `latest.json` 的更新說明也縮成一句話。
- **字體固定為 Grain 經典：** 操作文字用 Noto Sans TC，品牌標題「Grain」（相簿頂部、更新頁）用 Newsreader；沒有設定、沒有切換，也不再跟隨系統字體。字體是可變字重（Noto Sans TC 400–700、Newsreader 400–700，光學尺寸固定 24）的子集：Noto Sans TC 含拉丁、標點、注音、Big5 常用字與 App 目前所有文字用到的字，Newsreader 含拉丁與標點；子集以外的字由系統字體補上。字體為 SIL OFL 1.1，授權文字與來源附在 `assets/fonts/`，產生方式見 `scripts/prepare_fonts.py`，來源固定在 google/fonts commit `9710da1eacb3be272583c3224dcb70f9da6eadbb`。
- 前一輪研究見 [換字體研究](font-switching-research.md)；原本評估的「系統預設」選項依使用者決定不做。

## 驗證

2026-10-04 建置 `testDebugUnitTest`、`lintPersonal`、`assembleDebugAndroidTest`、`assemblePersonal` 成功，耗時 3 分 10 秒。178 項 JVM 測試通過，0 失敗／錯誤／跳過（0.7.1 為 175 項，新增更新提示規則 3 項）；lint 0 錯誤、30 項警告。`scripts/verify_fonts.py` 通過：兩個字體都保留 400–700 的 wght 軸，Noto Sans TC 涵蓋 App 用到的 344 個中文字與 Big5 常用字，授權檔齊全。這個腳本先在字體不存在時失敗，產生字體後才通過。

APK 確認 versionCode 35、versionName 0.7.2、非 debuggable，正式簽章憑證 SHA-256 `e6c756c9…c1217c8`（v2 簽章），16 KB ZIP 對齊與十款富士 LUT 原始 bytes 通過校驗；兩個字體在 APK 內與原始檔逐 byte 相同。APK 由 14,049,498 增加到 16,092,338 bytes（約 +2.0 MB）。

成品 `output/Grain-0.7.2-personal-fuji.apk`：SHA-256 `412d46cfe1f2a3c3820ccd7b84a32b6b9664f62c483bbea5893d575840b0e37d`。

2026-10-04 已推送 main 與 v0.7.2，標籤對應 `d1b56d736f850970edd402979ce42b3f92d0d143`。已發布 [公開 Release](https://github.com/waltwait/Grain/releases/tag/v0.7.2)，Release ID 403060485；草稿 APK 的大小與 SHA-256 核對後才公開。GitHub 最新版為 v0.7.2、非草稿、非預發布，只有一個 `Grain-0.7.2-personal-fuji.apk`，匿名下載連結回 200。`updates/personal-fuji/latest.json`（提交 `ea59654`）在附件確認後才更新為 0.7.2／35；repo 內的檔案與本機產生的完全相同。

要看到自動檢查的小金點，手機要先裝 0.7.2，再發布更高的版本；0.7.1 以前的 App 沒有開 App 時檢查的程式。

沒有手機連線：**字形、字重、行距與缺字備援沒有在實機看過**，小金點的位置與大小、自動檢查的實際網路行為也沒有實測；裝置測試沒有為這次的更新提示新增（結果取決於網路與線上版本）。

## 維護注意

App 新增含有罕見中文字的文字後，先跑 `python3 scripts/verify_fonts.py`：缺字會失敗，再以 `scripts/prepare_fonts.py` 重新產生字體子集。兩個腳本需要 fontTools 與 brotli（`pip install fonttools brotli`）。
