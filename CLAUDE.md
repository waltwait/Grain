# Grain — local and Claude cloud development

本文件在 repo 內自足；上層資料夾、本機記憶、簽章金鑰與未提交的檔案不會自動出現在雲端。`AGENTS.md` 指向本文件。

## 專案

原生 Android 拍照與錄影 App：Kotlin 2.3.20、AGP 9.1.1、Gradle 9.3.1、Compose BOM 2026.08.00、CameraX/OpenGL。JDK **17**、compile/target SDK **37**、Build Tools **36.0.0**、min SDK **29**；套件 `tw.luma.camera`。

回覆使用繁體中文。介面沿用黑金底片風格、Noto Sans TC UI 與 Newsreader 品牌字體；濾鏡品牌只有 **FUJIFILM / KODAK / GRAIN**，不要加次分類、操作教學或成功存檔通知。手機相機主畫面固定，橫拿只旋轉控制圖示，成品方向另判斷；相簿維持一般旋轉。

## 建置與驗證

```bash
# 日常修改：編譯及 JVM 測試，沒有簽章也可執行
bash tools/gradle.sh :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
# 合併前
bash tools/gradle.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s scripts -p 'test_ci_*.py'
# 正式包只在本機或 Actions release 環境
bash tools/gradle.sh :app:lintPersonal :app:assemblePersonal
```

- 不要每次清除 Gradle 快取或跑 `clean`；小改動先用編譯與有關的測試。不要在 Gradle 建置中途修改 app/test 原始碼。
- 測試只編譯未執行時明講「只編譯、未實機」。JVM/雲端建置不能證明 S24 色溫、CameraX 對焦、GPU LUT 色彩、錄影或觸控流暢性；這些在接手機的本機驗證。
- 同一工作樹可能有別的 session；先看 `git status`，只逐檔加入自己的變更，不用 `git add -A`。沒有使用者授權就不 commit/push/發版。
- 雲端設定見 `docs/cloud-development.md`；SessionStart hook 只在 `CLAUDE_CODE_REMOTE=true` 時處理 Linux 環境及還原 LUT，本機不動。

## 素材與簽章

- 官方十款富士 LUT 在 gitignore 的 `app/src/debug/assets/luts/fujifilm/`。用 `python3 scripts/restore_fuji_assets.py` 從公開 0.7.8 APK 還原並核對，不把 CUBE、APK 或金鑰加入 Git。
- **給使用者更新的一律是 personal 版**，完整十款富士 LUT、原正式簽章、非 debuggable。沒有金鑰或缺 LUT 就停止，不能改發普通 release/debug 版。
- 本機簽章仍讀 `.signing/release.properties`；Actions 簽章只從 `release` 環境的 secrets 提供。不要印出、提交或傳送金鑰、密碼至 Claude session、PR、log 或 artifact。
- 正式憑證 SHA-256：`e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8`。不可重建或替換。裝機只用 `adb install -r`，不能為了換簽章解除安裝。

## 自動發版

使用者要求新版時先 fetch main 及查看最新 Release，再同步增加 `versionName` 末碼及 `versionCode`；大版本由使用者決定。使用者可見的變更寫 `docs/releases/<versionName>.md`。

Claude cloud：推自己的分支、開 PR，合併 main 後由 `.github/workflows/release.yml` 打包、驗證、建立 `v<versionName>` 和單一 `Grain-<versionName>-personal-fuji.apk` Release。不要在雲端 session 推 tag、持有簽章或發 debug 包。已发布版本不重發；未完成發布可以重跑 workflow。

先核對公開附件大小／SHA-256，再更新 `updates/personal-fuji/latest.json`。App 與 Pages 共用此 feed，不必重新部署網站。詳細流程、dry run 與復原方式見 `docs/cloud-development.md`。
