# Grain 的 Claude cloud 與 Actions 建置

參考本機 `MySBLLM/assethub-android` 的 `CLAUDE.md`、`tools/cloud-env-setup.sh` 與 `.github/workflows/release.yml`，依 Grain 的版本、素材及 App 更新流程調整。

## 分工

| 位置 | 工作 |
| --- | --- |
| Claude cloud session | 開發、Kotlin 編譯、JVM 測試、裝置測試編譯、PR |
| GitHub Actions PR checks | 相同的 JVM 檢查、debug lint、裝置測試編譯，無簽章 secrets |
| 合併 main 後的 Release APK | 十款富士 LUT、原正式簽章、personal APK、驗證、Release、更新 feed |
| 使用者 Mac 與手機 | CameraX/GPU、對焦、色溫、錄影、觸控及覆蓋安裝驗證 |

相較 AssetHub：Grain 使用 JDK 17／SDK 37／Build Tools 36.0.0／AGP 9.1.1／Gradle 9.3.1，沒有需要私有 API 網址的後端。富士素材沒有提交 Git，正式包必須用 personal 而不是普通 release。App 與網站共同讀 `updates/personal-fuji/latest.json`。

## Claude 環境一次設定

在 [Claude Code](https://claude.ai/code) 新增獨立環境 **Grain Android**，選取 `waltwait/Grain`；單一 repo session 會載入本 repo 的 `CLAUDE.md` 及 `.claude/settings.json`。

1. Network access 選 **Custom**，勾 **Also include default list of common package managers**。
2. 加入以下網域（含 Gradle 及 GitHub APK 重新導向的下載主機）：

```text
dl.google.com
dl-ssl.google.com
maven.google.com
services.gradle.org
downloads.gradle.org
plugins.gradle.org
plugins-artifacts.gradle.org
repo.maven.apache.org
repo1.maven.org
github.com
api.github.com
raw.githubusercontent.com
release-assets.githubusercontent.com
objects.githubusercontent.com
```

3. Environment variables 填以下兩個；**簽章 secrets 不放在 Claude 環境**：

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_HOME=/opt/android-sdk
```

4. Setup script 貼 `tools/cloud-env-setup.sh` 的內容。已有 repo checkout 時亦可使用 `bash tools/cloud-env-setup.sh`。脚本安裝 JDK、Android SDK，核對 Google command-line tools SHA-256，不在啟動時跑完整 R8 或測試。
5. 每次 startup/resume 的 hook 確認工具鏈，從已公開 0.7.8 APK 還原十款富士 LUT；完整已有素材會先驗證而略過下載，本機 session 直接略過 hook。

Claude 的 Setup script 有 filesystem snapshot 快取；每次 session 的專案準備放 SessionStart hook，避免快取後少做設定。雲端沒有手機，因此裝置測試只能編譯。官方說明：[cloud environments](https://code.claude.com/docs/en/cloud-environments)、[SessionStart hooks](https://code.claude.com/docs/en/hooks#sessionstart)、[AGP 9.1.1 相容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)。

開始任務可貼：

> 先讀 CLAUDE.md。完成這次修改，執行有關的 JVM 測試及 Kotlin 編譯，推分支並建立 PR。只有我要求發新版時才增加 versionName/versionCode 與寫 docs/releases/版號.md；由合併 main 後的 Actions 正式發版。裝置測試未實機請明講。

## 簽章與素材

本機 `.signing` 留在原處。`tools/setup-release-secrets.py --upload` 先核對正式憑證，再只透過 stdin 設定以下 **release 環境 secrets**；環境只准 `main` 分支使用，沒有 repo-level fallback。

- `GRAIN_KEYSTORE_BASE64`
- `GRAIN_RELEASE_STORE_PASSWORD`
- `GRAIN_RELEASE_KEY_ALIAS`
- `GRAIN_RELEASE_KEY_PASSWORD`
- `GRAIN_RELEASE_STORE_TYPE`

Secrets 不加入 source、PR、log、Claude session 或 artifact；Actions 將金鑰還原到 `$RUNNER_TEMP/grain-signing`，最後清除。簽章工作只讀依賴快取，不回存可能帶有簽章資料的 build cache。PR 沒有 release 環境、也不使用 `pull_request_target`。

素材從現有公開版本還原：固定 0.7.8 APK 的下載網址與 SHA-256 `bf20c3cc63dc1cf4606b1b8136013f84fbeaceebf66bddde044e7c8ba6b0d35c`，只抽取十款 CUBE 與來源清單，逐檔核對。**不要刪除作為素材基準的 0.7.8 Release**。基準不存在、雜湊不符、缺 LUT 或憑證不符都停止；不會改發少富士濾鏡的 APK。沒有建立新的素材下載站。

## 發版及重跑

1. 同步增加 `app/build.gradle.kts` 的 `versionName`／`versionCode`，寫 `docs/releases/<versionName>.md`，通過 PR checks 後合併 main。
2. Actions 跑腳本檢查、JVM 測試、personal lint、裝置測試編譯及 `assemblePersonal`。
3. 驗證套件、版本、非 debuggable、原正式憑證、十款富士 LUT、16 KB ZIP 對齊。
4. 建 draft，只有一個 `Grain-<versionName>-personal-fuji.apk` 附件；核對 GitHub 回報的大小及 SHA-256 後發布為 Latest。
5. 確認公開 APK 可下載後，由 github-actions bot 提交新的 `updates/personal-fuji/latest.json`；App 與 Pages 自動看到新版，網站不必重部署。

測試 secrets 或打包設定：

```bash
gh workflow run release.yml --repo waltwait/Grain -f dry_run=true
```

dry run 只留下保留一天的正式 APK artifact，不建立 Release、tag 或修改 feed。已有版本也會重新驗證簽章，不會直接略過。

補發或恢復中斷的發布：

```bash
gh workflow run release.yml --repo waltwait/Grain -f dry_run=false
gh run list --repo waltwait/Grain --workflow release.yml --limit 3
gh run watch <run-id> --repo waltwait/Grain
```

已發布且 feed 完成的版本直接略過；發布中斷時會重新下載已上傳 APK 並驗證，再完成發布／feed。已有 draft 必須來自本次來源 commit；不會覆蓋另一個 commit 的附件。未完成上傳或多個附件要先人工檢查該 draft。更新 feed 採 main 最新內容並限制 versionCode 單調增加，不覆蓋其他已推進的新版。

rollout 先只允許手動 dry run；確認 GitHub runner 實際簽章與素材檢查成功後，再啟用 main 自動觸發。

SDK 安裝使用 `platforms;android-37.0`，與本機已安裝的 `android-37.0` 對應；API 37 的新版命名不能寫成 `platforms;android-37`。第一輪 dry run 停在 SDK 查找，未執行簽章或發布，已按實際 SDK package ID 修正。

2026-10-10 本機驗證：新工具指令完成 debug／personal Kotlin 與裝置測試 Kotlin 編譯、257 項 JVM 測試與 debug lint，建置 52 秒。10 項發布腳本測試通過，涵蓋雜湊不符、缺 LUT、拒絕覆蓋本機素材、附件數量／大小／SHA-256／網址、禁止降版、發布順序與中斷後復原；模擬發布測試沒有真正上傳附件。兩份 Actions YAML 通過 actionlint 1.7.12，Bash 語法及 hook JSON 檢查通過。本機正式憑證核對成功，五個簽章 secrets 已存至只允許 main 的 release 環境。

GitHub Ubuntu 24.04 驗證：[Android CI](https://github.com/waltwait/Grain/actions/runs/38055430014) 成功，耗時 5 分 18 秒，下載報告確認 257 項 JVM 測試、0 失敗／錯誤／跳過，debug lint 0 錯誤、30 警告；[正式 dry run](https://github.com/waltwait/Grain/actions/runs/38055456862) 成功，耗時 6 分 55 秒，完成原正式簽章、十款富士、版本、非 debuggable 與 ZIP 對齊驗證。確認後啟用 main 自動觸發；dry run 沒有發布 Release 或修改 feed。兩輪有舊 Actions Node.js 20 的停用提示，runner 轉用 Node.js 24 後均執行成功。

Mac 畫面鎖定，未代操作 Claude 帳號的環境對話框，Claude cloud VM 的啟動 hook 也尚未在實際 Claude session 執行；本機確認 local session 會略過 hook。Claude 的一次設定依上方步驟完成後即可使用。
