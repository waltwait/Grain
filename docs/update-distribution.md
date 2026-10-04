# Grain 下載頁與 App 更新方案

> 後續：2026-10-04 使用者將 `waltwait/Grain` 改為 public，App 預設改讀公開 raw `latest.json`；最新狀態與發布順序見 [0.7.0 發布紀錄](release-v070.md)。以下為較早的研究與當時決定。

2026-10-03 研究。最新決定為 private 儲存庫與原個人 APK 發布方式；原 Grain Pages 已關閉，公開網頁改由獨立 Grain-pages 連到私人 Release。0.6.3 的「檢查更新」開啟 GitHub 最新 Release，使用者登入後下載及安裝。原生 HTTPS 更新模組保留供日後配置服務；私人 GitHub 不能直接作為匿名 App 更新來源。見 [私人更新紀錄](private-updates-v063.md)。

## 建議架構

程式碼與正式簽章繼續留在私人 `waltwait/Grain`。網站提供下載頁、版本資訊與一個已簽章 APK；實際網站／網域與存取方式待確認。獨立公開下載儲存庫 `Grain-downloads` 是一般可分發包的備選，尚未建立。

每次發布時，核對 APK 的套件、versionCode、簽章與 SHA-256，再同步版本 APK 與資訊檔。網站與 App 共用 `latest.json`，不用把 GitHub 權杖放進網站或 App。個人富士包與一般 release 有獨立通道，個人包更新必須保留十款富士 LUT；不能因網站分發而替換成沒有富士素材的包。公開下載的素材範圍另行確認。

GitHub Pages 是靜態 HTML／CSS／JavaScript 託管，頁面讀取版本 JSON 即可動態顯示新版。公開儲存庫可使用 GitHub Free；私人來源的 Pages 取決於帳號方案。私人 Release 的 API 與附件不能因為網頁公開就自動變成匿名可下載，需另提供公開 APK 或帶登入驗證的後端。[Pages 官方文件](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages)、[Release API 官方文件](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。

## 使用介面

- 下載頁只顯示版本、簡短更新內容與一個「下載 APK」按鈕。AAB 留作上架準備；checksum 放在版本 JSON，不要求一般使用者選檔案。
- App 的設定加入「版本」列，顯示目前版本、最新版與「檢查更新」。有新版時顯示「下載更新」，下載完成再顯示「安裝更新」。
- 本次依使用者要求採手動檢查、下載及安裝；網路及校驗採背景 I/O，進入更新頁暫停相機。背景排程及 Wi-Fi 自動下載留作後續需求。
- 下載完成先核對大小與 SHA-256，再核對 APK 套件名、versionCode 及與已安裝 App 相容的簽章。只接受較高 versionCode；不把 AAB、debug 包或不同簽章包當成可直接更新的正式 APK。

Android 網站分發需處理「允許安裝未知 App」授權。[Android 官方分發文件](https://developer.android.com/distribute/marketing-tools/alternative-distribution)。本次以受限 FileProvider URI 開啟系統安裝畫面，使用者明確按安裝；沒有宣稱無提示安裝。[FileProvider 官方文件](https://developer.android.com/reference/androidx/core/content/FileProvider)。Android 12 以上在符合 target SDK、權限與自行更新等條件時可免部分確認，但仍需處理使用者操作；這不是本次選定的流程。[PackageInstaller 官方文件](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))。

若日後使用 Google Play 分發，改接 Play In-App Updates；它是 Google Play 的更新流程，與目前的 GitHub APK 分發不同。[Play 官方文件](https://developer.android.com/guide/playcore/in-app-updates)。

## 版本資訊範例

由已簽章 APK 自動產生。以下使用已發布 0.6.1 個人包數值與**示意 HTTPS 網址**，尚未部署；不能沿用私人附件當成匿名網址。

```json
{
  "schemaVersion": 1,
  "packageName": "tw.luma.camera",
  "versionName": "0.6.1",
  "versionCode": 24,
  "minSdk": 29,
  "channel": "personal-fuji",
  "bundledFujiCount": 10,
  "apkUrl": "https://updates.example.invalid/grain/Grain-24.apk",
  "apkSize": 13966866,
  "apkSha256": "9071035eec1a7fc218e31015fcb72b0bc21f92fd5943897a7aa01b6edd7c3457",
  "signingCertificateSha256": "e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8",
  "notes": "恢復十款富士濾鏡"
}
```

## 待決定的下載對象

| 對象 | 可行方式 |
| --- | --- |
| 所有人 | 建議獨立公開 APK 下載區，程式碼維持私人；Pages 與 App 可匿名讀取版本資訊。 |
| 本人／受邀使用者 | 維持私人個人包；完整 App 內下載需真正的登入／受控下載服務，瀏覽器登入 GitHub 不能代替 App 內下載。 |

使用者曾選擇公開 GitHub raw／Release／Pages，之後明確改回 private 並維持原發布方式。公開方案停止，0.6.2 保留草稿，最新私人流程見 [0.6.3 紀錄](private-updates-v063.md)。
