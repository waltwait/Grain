# Grain 下載主頁

2026-10-05 起主頁放在這個 repo 的 `website/`，網址 <https://waltwait.github.io/Grain/>。舊的獨立 repo `waltwait/Grain-pages`（網址 `…/Grain-pages/`）只剩一個導向新網址的頁面，可以封存。

## 為什麼搬回來

先前主頁放在另一個 repo，是因為當時 Grain 是 private、免費方案的私人 repo 不能用 Pages，下載按鈕也只能連到要登入 GitHub 的私人 Release。0.7.0 起 repo 改為 public，這個理由不存在了。另一個問題是舊主頁的版本資訊 `latest.json` 要手動同步，0.6.9 之後沒有同步，線上一直顯示 0.6.9。

## 運作方式

- 頁面載入時從 `https://raw.githubusercontent.com/waltwait/Grain/main/updates/personal-fuji/latest.json` 讀版本資訊，所以**發布新版後主頁不用重新部署**，也不用手動同步；raw 網址有最多 5 分鐘的快取。
- 下載按鈕直接連到該版本 Release 的 APK，不需要登入。頁面只接受 `https://github.com/waltwait/Grain/releases/download/…` 的連結，並檢查套件名、版本號、大小、雜湊與簽章憑證欄位，格式不對就不顯示下載按鈕。
- 頁尾有「所有版本」（Releases 頁）與 GitHub repo 連結。
- `.github/workflows/pages.yml` 只在 `website/` 或該 workflow 有變動時，把 `website/` 資料夾部署到 Pages（Pages 來源設為 GitHub Actions）；`docs/` 等內部文件不會被發布。
- 網站測試：`node --test scripts/test_update_site.cjs`（7 項，不需要瀏覽器與網路）。

## 注意

- 主頁提供的是個人富士版 APK（內含十款官方富士 LUT），與 Release 頁上的相同；若日後要公開不含富士素材的版本，需要另外發布一般版並讓頁面指向 `updates/release/latest.json`。
- 舊的 `scripts/prepare_private_download_page.py` 是為舊的獨立 repo 準備的，已不再使用。
- 網站圖示（favicon、apple-touch、連結預覽圖）與 App 圖示由 `scripts/render_icon.py` 從同一個標誌產生，改圖示時重新執行即可。
