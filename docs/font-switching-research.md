# Grain 換字體研究

> 後續：使用者決定不做設定切換與「系統預設」選項，0.7.2 起固定使用 Grain 經典字體（Noto Sans TC＋Newsreader），見 [0.7.2 發布紀錄](release-v072.md)。以下保留當時的研究與數據。

2026-10-04 研究，尚未實作。問題是「App 能不能讓使用者在設定裡換字體、要怎麼做、APK 要多大」。結論：可行，建議把字體打包進 App，用 Compose 的 `FontFamily` 加上 `MaterialTheme` 的 `Typography` 一次套用，換字體即時生效不用重啟；中文字體必須子集化才不會讓 APK 暴增。

## 目前狀態

整個 UI 都是 Compose，只有 `MainActivity` 的 `MaterialTheme(colorScheme = …)`，沒有自訂 `Typography`，也沒有任何 `res/font`。字體沿用系統預設，Samsung 手機在系統設定換字型時，Grain 會跟著換，這一條現在就能用，不需要任何改動。App 內沒有地方指定 `fontFamily`，所以只換 `Typography` 就能涵蓋所有文字。

## 做法比較

| 做法 | 運作方式 | 限制 | 建議 |
| --- | --- | --- | --- |
| 打包字體 | 字體放 `res/font`，建立 `FontFamily`，用 `MaterialTheme(typography = …)` 套用 | APK 變大；中文字體要子集化 | 建議 |
| Google Fonts 下載式 | `ui-text-google-fonts` 用 `GoogleFont.Provider` 經 Google Play services 下載 | 需要網路與 Play services，首次要連線，逾時會退回預設字體，要另備離線備援字體 | 不建議，相機 App 不該依賴網路才有一致外觀 |
| 只跟系統字體 | 不做事 | 使用者要到手機系統設定換，無法只改 Grain | 保留為「系統預設」選項 |

可變字體（一個檔含多種字重）的程式端用法是 `Font(R.font.xxx, variationSettings = FontVariation.Settings(FontVariation.weight(500)))`，官方標示需要 Android 8.0（API 26）以上；Grain 的 minSdk 是 29，沒有問題。依據：[Compose 字體文件](https://developer.android.com/develop/ui/compose/text/fonts)、[下載式字體文件](https://developer.android.com/develop/ui/views/text-and-emoji/downloadable-fonts)。

## 實測體積

方法：從 google/fonts 取得原檔，用 fonttools 子集化，字集為拉丁與標點、注音符號，加上 Big5 常用字 5,401 字（或加上次常用字共 13,051 字），保留 kern、liga 等排版特性，再用 deflate 估算進入 APK 後的大小。在這台機器量測，不代表其他壓縮設定。

| 項目 | TTF | APK 內約 |
| --- | ---: | ---: |
| Noto Sans TC 原檔（可變，完整） | 11,662 KB | 約 11.6 MB |
| Noto Sans TC 常用字，可變字重（保留 wght 軸） | 3,094 KB | 2,066 KB |
| Noto Sans TC 常用字，靜態 Regular＋Bold | 1,849＋1,846 KB | 1,162＋1,176 KB |
| Noto Sans TC 常用＋次常用，可變字重 | 7,806 KB | 5,054 KB |
| Noto Sans TC 常用＋次常用，靜態 Regular | 4,651 KB | 2,832 KB |
| Newsreader（拉丁）可變字重 | 263 KB | 161 KB |
| DM Sans（拉丁）可變字重 | 165 KB | 75 KB |

目前個人版 APK 約 14.0 MB。建議組合「Noto Sans TC 常用字可變字重＋Newsreader 可變字重」約增加 **2.2 MB**（約 +16%），App 內更新的下載量同步增加。可變字重與兩個靜態字重的體積差不多，但可變字體能直接提供 UI 用到的 Medium 與其他字重，所以建議用可變。Noto Serif TC 這次沒有量測，預期同級，實作前要再量。

## 缺字與授權

- 子集化後缺的字（常用字以外的生僻字或人名用字）預期會由系統字體補上，Compose 沿用 Android 的字體備援。這一點尚未在手機上確認，實作時要加入含生僻字的實機檢查。
- 候選字體 Noto Sans TC、Noto Serif TC、Newsreader、DM Sans 都是 SIL OFL 1.1，允許打包與子集化；需隨 App 附上授權文字與來源。實作時要核對各字體的授權檔與保留字體名稱條款，現有 Kodak LUT 的 `ATTRIBUTION.txt` 可作為附上方式的範例。上次的字型對照圖已記錄來源，存於被 Git 忽略的 `output/font-preview/`。

## 建議的方案

設定新增「字體」一列，提供 2 到 3 個選項：

1. **系統預設**：現況，0 KB，跟隨手機字體。
2. **Grain 經典**：品牌標題 Newsreader，其餘操作文字 Noto Sans TC（上次預覽的推薦組合）。
3. 視需要再加一個全襯線 Noto Serif TC，每多一個中文字體約加 2 MB 以上。

選擇存在既有的 SharedPreferences，`MainActivity` 依狀態切換 `Typography`，即時重組、不用重啟。品牌標題「Grain」目前是在 `GalleryScreen` 直接指定 22sp 粗體，換字體時要改成讀字體設定。

## 尚未驗證

- 手機上的實際字形、字重、行距與缺字備援。
- 首次載入大字體檔的啟動與切換時間。
- 增加 APK 體積後對 R8、16 KB 對齊與更新下載的影響。
- Serif TC 的體積。
