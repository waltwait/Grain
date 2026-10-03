# Grain 0.6.2：App 內更新

狀態：0.6.2 已接上 GitHub 固定更新通道，成品驗證完成並上傳草稿；儲存庫已按使用者要求改為 public。含十款富士的 0.6.2 公開發布被自動核准審查拒絕，等待使用者明確確認及核准；目前公開最新版仍是 0.6.1，見 [公開更新來源](public-updates-v062.md)。完整 App 內更新仍待 0.6.2 發布及手機驗證。

## 操作

設定 → 檢查更新 → 下載新版 → 安裝。點設定內的「檢查更新」就開啟更新頁並開始檢查。

更新頁沿用黑金主題，顯示目前版本、最新版、短更新內容與一個主要按鈕。下載顯示百分比及取消按鈕，失敗留在頁面內。沒有成功通知、手勢說明或啟動時的強制更新彈窗。進入更新頁暫停相機，返回後恢復原本的濾鏡與拍攝設定；錄影及存檔中不開放進入。

首次安裝若尚未授權 Grain，開啟 Android 的來源安裝設定，返回後核對授權，再開啟系統安裝畫面。取消系統安裝後仍可再按安裝，不需要重新下載。App 不宣稱安裝成功，實際安裝由 Android 處理。

第一次把此功能帶進手機仍需要手動安裝同簽章的個人 APK；舊 0.6.1 沒有更新模組。覆蓋更新沿用 `tw.luma.camera` 與正式簽章，保留 App 資料。舊 debug 包簽章不同，不能直接覆蓋。

## 版本資訊與下載

網站與 App 共用 `latest.json`。`UpdateInfo` 檢查 schema、套件、整數版本號、Android 要求、素材通道、檔案大小、SHA-256、憑證雜湊及 HTTPS URL。比較以 versionCode 為準。

`personal-fuji` 只接受包含十款富士 LUT 的個人包；一般 `release` 使用獨立通道。下載後再檢查 APK 的實際套件、版本、minSdk、非 debug、簽章與十款素材，避免個人版被沒有富士素材的包覆蓋。兩個通道沿用既有簽章。

下載與校驗在背景 I/O 執行，進度最多每秒更新五次。只寫入專用快取 `grain-updates/`，先串流寫 `.part`、限制大小並核對 SHA-256，成功才改為 APK。取消會刪除未完成檔案，後續操作等待前一次清理完成，避免重試互相刪檔。下載檔案在安裝前再次校驗；FileProvider 只開放這個目錄的臨時讀取權。

網路連線只使用 HTTPS，限制重新導向、版本描述大小與 APK 大小，處理逾時、網路失敗與空間不足。沒有在 App 或網站內嵌 GitHub 權杖。受登入保護的網站需要另接真正的登入／受控下載服務；私人 GitHub 附件不能直接作為匿名更新來源。

## 網站準備與發布

`website/` 是黑金下載頁範本，只提供一個最新版 APK 按鈕。頁面讀取相對路徑 `latest.json`，更新描述使用文字呈現；資料缺失或不合法時停用下載並提供重試。原始碼不帶 APK、富士素材、簽章私鑰或帳號權杖。

個人建置預設使用 `https://raw.githubusercontent.com/waltwait/Grain/main/updates/personal-fuji/latest.json`，一般建置使用獨立的 `release` 通道。需要自架來源時可覆寫：

```sh
./gradlew :app:assemblePersonal -PgrainUpdateUrl=https://YOUR-HOST/grain/latest.json
python3 scripts/verify_personal_fuji_apk.py app/build/outputs/apk/personal/app-personal.apk
```

`YOUR-HOST` 是自架來源示意文字，需替換為已驗證的正式網站。不傳屬性時沿用 GitHub 固定來源；只有明確傳入空白覆寫時，才顯示「更新下載網站尚未設定」。

以同一個已簽章 APK 自動產生網站檔案，APK URL 需指向該次版本檔案：

```sh
python3 scripts/prepare_update_site.py app/build/outputs/apk/personal/app-personal.apk \
  --apk-url https://YOUR-HOST/grain/Grain-25.apk \
  --build-tools /path/to/Android/sdk/build-tools/36.0.0 \
  --notes-file /path/to/release-notes.txt
```

執行前設定 JDK 17 的 `JAVA_HOME`。腳本透過 aapt2 讀取 APK metadata、apksigner 驗證正式憑證、既有富士腳本驗證十款檔案原始雜湊；產生 APK、HTML／CSS／JS 與 `latest.json`，存於被 Git 忽略的 `output/update-site/`。只產出本機檔案，不執行部署。

發布時先上傳不可變的版本 APK，再更新 `updates/<channel>/latest.json`。新版沿用同一個資訊網址；資訊檔讀取會要求重新驗證快取，版本 APK 可保留長快取。使用者已於 2026-10-03 要求原儲存庫 public；現有 Release 附件也會公開，第三方素材仍沿用原來源與條款說明。

## 驗證

目前 JVM 更新測試覆蓋版本比較、metadata、通道、簽章／套件／版本不符、富士缺少、損壞或超限下載、雜湊與取消。裝置測試包含更新頁進出／重新建立與相機狀態、FileProvider 目錄限制及安裝權限宣告；未連接手機時僅編譯，不回報已執行。

2026-10-03 本機驗證：143 項 JVM 全數通過；4 項網站測試通過；lintPersonal 0 錯誤、22 警告（含原有警告與空間配置建議）；裝置測試 APK、個人 APK 及一般 release APK 編譯成功。使用明確的 `.invalid` HTTPS 測試網址驗證 R8 下保留更新路徑，這不是部署網址。

個人測試成品為 `output/Grain-0.6.2-updater-dev.apk`，code 25、非 debug、沿用正式憑證；十款富士原始 bytes 及 23 個既有二進位 LUT／native library 項目保持一致，16KB ZIP alignment 檢查通過。APK 大小 14,000,346 bytes，SHA-256 `f3f23e0ae3cdca9f9a274378e72dfa24ec18eade9138aa2f8e0a6c8b65c5eaea`。這個 APK 僅供本機開發驗證，不提供為可用的更新 Release。

正式發布前需要：

- 實際網站回傳的資訊與已簽 APK 一致，重新導向全程 HTTPS。
- 0.6.1 手動覆蓋為首個含更新模組版本，之後用較高 versionCode 測試 App 內下載／安裝。
- Samsung S24 Android 16 首次授權、拒絕授權、取消安裝、網路中斷、取消及重試。
- 覆蓋更新後設定、匯入 LUT 與十款富士濾鏡仍存在；照片與影片仍可讀取。

## 官方依據

- [Android 網站分發與來源授權](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
- [PackageManager 安裝來源授權](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
- [Settings 安裝來源設定](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES)
- [FileProvider 臨時檔案讀取權](https://developer.android.com/reference/androidx/core/content/FileProvider)
