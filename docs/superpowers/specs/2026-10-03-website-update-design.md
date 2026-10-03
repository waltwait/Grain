# Grain 網站分發與 App 更新設計草案

狀態：使用者最新選擇改回 private 與原個人 APK 發布方式，覆蓋先前 public／Pages 部署選擇。Pages 已關閉，0.6.2 保留草稿；0.6.3 預設「檢查更新」開啟私人 GitHub Release，登入後下載並安裝。此文件其餘原生更新流程保留作為配置獨立 HTTPS 服務時的設計，最新執行狀態見 [私人更新紀錄](../../private-updates-v063.md)。

## 使用者需求與目前狀態

使用者要透過網站取得 Android APK，App 能顯示最新版並下載更新。網站只需一個 APK 下載按鈕；相機介面保持簡潔，不增加手勢說明、成功通知或強制更新彈窗。保留十款富士 LUT 與現有照片編輯、變焦及相簿功能。

目前 `waltwait/Grain` 是私人儲存庫，最新已發布版本 0.6.1／versionCode 24。個人 `personal` APK 使用正式簽章並包含十款富士 LUT，原 `release` 包沒有這些素材。0.6.2／versionCode 25 開發版新增更新模組與網路／安裝權限。既有下載研究見 [分發方案](../../update-distribution.md)，富士修復與簽章見 [0.6.1 紀錄](../../personal-fuji-v061.md)。

## 固定的操作流程

設定新增「檢查更新」入口，點擊即開啟更新頁並檢查。更新頁顯示目前版本、最新版、短更新內容與單一主要按鈕。狀態依序為檢查、可下載、下載進度、可安裝；無新版顯示「已是最新版」。失敗在頁內顯示可重試訊息，取消安裝可回到可安裝狀態，不重複下載已驗證檔案。

第一次按「安裝更新」時，若 Grain 沒有來源安裝授權，開啟 Android 的「允許安裝未知 App」設定，回來重新檢查授權，再由明確操作啟動系統安裝畫面。只在使用者要安裝時請求這項授權。網站直接下載時，安裝來源是執行下載的瀏覽器，所需授權歸瀏覽器；App 自行下載時授權歸 Grain，兩者不能混用。

App 更新頁以 Compose 實作並沿用黑金主題。下載、雜湊與 APK metadata 檢查放在背景執行，不阻塞相機控制。下載後保留使用者的設定、已匯入 LUT 與照片；安裝檔進入專用快取目錄，不寫入照片相簿。首版提供手動檢查、下載及安裝；背景排程與 Wi-Fi 自動下載作為後續獨立選項。

## 網站與版本資訊

下載頁顯示 Grain、最新版本、短更新內容與一個「下載 APK」按鈕。AAB、checksum 與簽章不是一般使用者需要選擇的附件。網站與 App 共用由成品自動產生的版本描述。

版本描述必須含 schemaVersion、套件名、versionName、versionCode、minSdk、通道、是否含富士素材、APK 大小、SHA-256 與下載位置。`personal-fuji` 與一般 `release` 分開識別；安裝了個人富士版的 App 只接受 `personal-fuji` 更新，不因一般 release 的版本號較高而切換素材組合。個人素材版本發布前仍執行 `verify_personal_fuji_apk.py`。

下載先寫入暫存檔，完整核對大小及 SHA-256 才發布為可安裝檔案。核對 APK 的套件名必須為 `tw.luma.camera`、versionCode 高於目前版本、Android minSdk 相容且簽章與已安裝版本相容；不同簽章、debug、損壞檔案與降版都顯示頁內錯誤。系統安裝器負責實際簽章驗證與安裝。只允許 HTTPS，處理逾時、取消、重新導向與可用空間，不向任意下載主機傳送帳號憑證。

正式 key 保持不變。第一次把更新模組帶入手機，仍需手動下載新的同簽章個人 APK；完成這次更新後才能使用 App 內更新入口。

## 部署分支與待決定事項

| 方案 | 下載方式 | 與目前富士版的關係 |
| --- | --- | --- |
| 私人下載網站 | 使用者登入後取得受控 APK；完整 App 內下載需網站提供登入與下載服務。 | 保留現有個人素材範圍；建議依目前需求優先採用。 |
| 公開下載網站 | 靜態下載頁與匿名 HTTPS 版本資訊／APK，可用獨立 Pages 下載區。 | 公開分發十款官方素材需另確認，不能直接把私人個人 APK 公開。 |
| 網站導向私人 GitHub | 網站只提供固定最新版連結，由瀏覽器登入 GitHub 下載。 | 可立即沿用私人包，但屬瀏覽器下載，不能稱為完整 App 自動下載。 |

使用者已選定私人 GitHub 的原發布方式：App 開啟最新 Release，下載由已登入的瀏覽器處理。預設不內嵌帳號權杖，不向私人 raw URL 發起匿名檢查；保留可配置的原生更新服務入口。

## 實作與驗證範圍

共同模組包含版本資料與通道判斷、受限的 HTTPS 下載、雜湊與 APK 檢查、安裝授權與系統安裝入口、Compose 更新頁，以及網站版本描述產生器。登入與下載位置依部署分支確定後納入相應介面。

單元檢查涵蓋版本比較、個人通道保留、metadata 欄位、下載大小與雜湊、取消及損壞檔案。裝置檢查涵蓋安裝來源授權、拒絕／取消、下載中旋轉與背景恢復、同簽章覆蓋更新後偏好與匯入 LUT 保留，以及更新後十款富士濾鏡仍可用。沒有手機時只回報編譯與靜態成品檢查，不宣稱實機更新已通過。

## 官方依據

- [Android 網站分發與來源授權](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
- [PackageManager 安裝來源授權檢查](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
- [FileProvider 與安裝檔臨時讀取權](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [GitHub Release API 與私人資源存取](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)

原生更新共同模組已實作及本機驗證；0.6.3 預設採私人 Release 瀏覽器流程，手機安裝與返回相機仍待實機驗證。
