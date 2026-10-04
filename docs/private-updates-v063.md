# Grain 0.6.3：私人個人版更新

> 後續：2026-10-04 儲存庫改為 public，0.7.0 起預設更新網址恢復，不再留空；見 [0.7.0 發布紀錄](release-v070.md)。以下保留 0.6.3 當時的 private 流程。

使用者於 2026-10-03 改選 private 儲存庫與原個人 APK 發布方式。已確認 `waltwait/Grain` 的 `private: true`、`visibility: private`、`has_pages: false`。原 Grain Pages 已關閉，自動部署 workflow 移除；0.6.2 留作草稿，不修改既有 tag 或歷史附件。

## 預設流程

設定 → 檢查更新 → 更新頁 → 檢查更新 → GitHub 最新 Release。

外部瀏覽器或 GitHub App 開啟 `https://github.com/waltwait/Grain/releases/latest`，以有儲存庫存取權的 GitHub 帳號登入、下載 APK，再由 Android 安裝。私人 Release 的版本資訊與附件需要登入，Grain 不內嵌帳號權杖，不把瀏覽器登入當成原生下載登入。這個版本保留更新入口，但不提供匿名自動下載或背景安裝。

另以公開的 `waltwait/Grain-pages` 儲存庫發布網頁，網址為 `https://waltwait.github.io/Grain-pages/`。GitHub 回覆目前方案不支援私人儲存庫 Pages，因此採獨立網頁庫；公開內容僅有 HTML、CSS、JavaScript 與版本 metadata，沒有 APK、LUT、簽章私鑰或帳號憑證。網頁顯示最新版本、簡短更新內容與單一 APK 按鈕，連到私人最新 Release，並顯示「登入 GitHub 下載」。

更新頁保持目前版本與單一主要按鈕；沒有操作說明、成功通知或彈窗。進入頁面暫停相機，返回恢復原拍攝狀態。預設 `UPDATE_FEED_URL` 留空，不自動檢查私人 raw URL，也不顯示網站未設定的錯誤；外部頁面開啟失敗才顯示頁內錯誤。

## 版本與資料

0.6.3／versionCode 26 使用 `personal` build type，套件仍為 `tw.luma.camera`，非 debug，包含原十款官方富士 LUT。正式簽章沿用 0.6.0／0.6.1；同簽章覆蓋安裝保留 App 資料，沒有變更偏好或已匯入 LUT 的格式。舊 debug 簽章不能直接覆蓋。

Release 只提供 `Grain-0.6.3-personal-fuji.apk` 一個附件。舊 Release 保留，不刪除或替換歷史 APK。`updates/personal-fuji/latest.json` 保留成品資訊供維護，不作為預設匿名下載服務。

原生 HTTPS 更新、通道校驗、下載大小與 SHA-256、正式簽章驗證和系統安裝器模組仍保留；日後另有可用服務時，可用 `-PgrainUpdateUrl=https://YOUR-HOST/grain/latest.json` 配置。個人通道仍要求十款富士 LUT，不能被一般無富士包覆蓋。

## 驗證與發布

0.6.3 編譯成功（1 分 28 秒）。143 項 JVM、5 項網站測試通過；lintPersonal 0 錯誤、22 警告，裝置測試 APK 及個人／一般 APK 編譯成功。預設 debug、personal、release 的更新 feed 均確認為空白。十款富士原始 bytes、原正式簽章與 16KB ZIP alignment 通過核對。沒有手機連線；Samsung S24 Android 16 的 GitHub 登入、下載安裝與返回相機待實機驗證。

成品 `output/Grain-0.6.3-personal-fuji.apk`：14000342 bytes；SHA-256 `eeb0ff2a6b782f82b394c77b17414df08befd4503791caa7a2cf0ef4dc382193`。

## 後續維護

發布每個新私人 Release 後，以相同簽章 APK 執行 `scripts/prepare_private_download_page.py`，明確指定該版本私人 APK URL、build-tools 與更新內容。腳本只輸出五個網頁檔案，不複製 APK；若目錄有其他檔案會拒絕。將輸出同步到 `Grain-pages` 的 main 分支，Pages 會重新建置。網頁的按鈕固定連 `/releases/latest`，不需逐版更換下載連結；顯示版號與說明由同步後的 `latest.json` 更新。

## 發布結果

2026-10-03 已發布 [私人 Grain 0.6.3](https://github.com/waltwait/Grain/releases/tag/v0.6.3)，只有 `Grain-0.6.3-personal-fuji.apk` 一個附件；GitHub 回報大小 14,000,342 bytes 與 SHA-256 和本機成品一致。原儲存庫再次確認 `private: true`、`has_pages: false`；不帶登入憑證讀取最新 Release API 回傳 404。0.6.2 仍保留草稿。

[公開下載頁](https://waltwait.github.io/Grain-pages/) 由獨立公開網頁庫部署，Pages 回報 `public: true`、`status: built`，[部署 37121144873](https://github.com/waltwait/Grain-pages/actions/runs/37121144873) 成功。遠端只含五個網頁／metadata 檔及 README，未發布 APK 或 LUT。瀏覽器實際顯示 0.6.3、13.4 MB、「登入 GitHub 下載」與唯一下載按鈕，目標為私人 `/releases/latest`；匿名取得的公開 JSON 和本機 metadata 完全一致。
