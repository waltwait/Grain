# 編輯成品獨立存放與原圖切換 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 編輯匯出的照片存到獨立資料夾 `Pictures/Grain Edits`，每次成對保存改完圖與原圖副本，相簿「編輯」tab 顯示成品並可切換看原圖。

**Architecture:** `PhotoStorage.processAndSave` 新增 `SaveTarget`（相機／編輯）決定資料夾、檔名前綴與是否固定存原圖；配對只靠同一組基底檔名，不用資料庫。`GalleryViewModel` 另查編輯資料夾，用純函式 `EditPairing` 把改完圖與同組 `_original` 配對；UI 在編輯 tab 首頁顯示成品格子牆，檢視頁提供原圖／改完切換。

**Tech Stack:** Kotlin、Jetpack Compose、MediaStore、JUnit4（JVM）、AndroidX Test（裝置測試，目前沒有手機，只能編譯）。

**Spec:** `docs/superpowers/specs/2026-10-04-separate-edit-storage-design.md`

## Global Constraints

- 編輯資料夾 `Pictures/Grain Edits`，查詢用含結尾斜線的 `Pictures/Grain Edits/`；相機維持 `Pictures/Grain`、`Pictures/Grain/`。
- 檔名：編輯 `GRAIN_EDIT_<時間>.jpg` 與 `GRAIN_EDIT_<時間>_original.jpg`；相機 `GRAIN_<時間>.jpg` 與 `GRAIN_<時間>_original.jpg`；`<時間>` 沿用 `yyyyMMdd_HHmmss_SSS`。
- 編輯目標固定寫入原圖副本，不受相機「同時儲存原圖」開關影響；相機行為完全不變。
- 原圖副本是來源檔的 byte 複製；兩個檔案都成功才公開，任一失敗就刪除已寫入的檔案並拋出錯誤。
- 既有照片不搬動，不用資料庫或額外索引。「照片」tab 的查詢不變。
- 編輯存完不更新相機左下角縮圖，只重新整理相簿。
- 使用者介面文字為繁體中文；minSdk 29。
- 建置環境：`export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`，Gradle 一律加 `--offline`。
- 沒有手機連線：裝置測試只能用 `assembleDebugAndroidTest` 編譯，任何文件與回報都不得寫成「已執行」。
- Commit 訊息結尾加：`Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`。只 `git add` 該任務列出的檔案，不提交 `.signing/` 或 `output/`。

## Review Focus

- 編輯資料夾還不存在（首次使用）或沒有任何成品：查詢回空清單，編輯 tab 只顯示「選擇照片」按鈕、不顯示空格子牆。（Task 4、5）
- 改完圖存在但原圖被使用者在其他 App 刪掉：該項目 `original` 為 null，檢視頁不出現切換、不當機。（Task 4、6）
- 只剩 `_original` 檔而改完圖被刪：不得出現在格子牆。（Task 4）
- MediaStore 因同名自動改名（例如 `GRAIN_EDIT_x (1).jpg`）：找不到同組原圖，視為沒有原圖，不誤配到別組。（Task 4）
- 切換到原圖後翻頁或再進入檢視頁：預設回到改完圖，不殘留上一張的「看改完」狀態。（Task 6）

---

### Task 1: 提交已完成的「存完離開編輯 tab 重置」修正

這個修正已實作在工作區，與本計劃的編輯 tab 首頁互相依賴（存完重置後才會回到首頁看到新成品），先獨立提交。

**Files:**
- Commit: `app/src/main/java/tw/luma/camera/editor/PhotoEditorViewModel.kt`、`app/src/main/java/tw/luma/camera/ui/GalleryScreen.kt`、`app/src/test/java/tw/luma/camera/editor/PhotoEditorUiStateTest.kt`、`app/src/androidTest/java/tw/luma/camera/ui/CameraExperienceDeviceTest.kt`

**Interfaces:**
- Produces: `PhotoEditorUiState.finished: Boolean`；`GalleryScreen` 在「照片」tab 停穩且匯入編輯 `finished` 時呼叫 `importEditor.discard()`。

- [ ] **Step 1: 驗證** — Run: `./gradlew :app:testDebugUnitTest --offline`；Expected: BUILD SUCCESSFUL，`PhotoEditorUiStateTest` 3 項通過。
- [ ] **Step 2: 提交** — 只 add 上面四個檔案，訊息 `Reset a finished import when the editor tab is left`。

---

### Task 2: 存檔去向與檔名（純函式）

**Files:**
- Create: `app/src/main/java/tw/luma/camera/storage/PhotoNames.kt`
- Test: `app/src/test/java/tw/luma/camera/storage/PhotoNamesTest.kt`

**Interfaces:**
- Produces:
  - `enum class SaveTarget(val relativePath: String, val prefix: String, val alwaysSaveOriginal: Boolean)`，成員 `CAMERA("Pictures/Grain", "GRAIN_", false)`、`EDIT("Pictures/Grain Edits", "GRAIN_EDIT_", true)`，屬性 `val queryPath: String`（`relativePath + "/"`）。
  - `object PhotoNames`：`fun edited(target: SaveTarget, time: String): String`、`fun original(target: SaveTarget, time: String): String`、`fun isOriginal(name: String): Boolean`、`fun originalOf(editedName: String): String?`（改完圖檔名換成同組原圖檔名；輸入不是 `.jpg` 或本身就是原圖時回 `null`）。

- [ ] **Step 1: 寫失敗測試** — `PhotoNamesTest`：
  - `editedAndOriginalNamesUseTheTargetPrefix`：`edited(EDIT, "20261004_201530_123") == "GRAIN_EDIT_20261004_201530_123.jpg"`、`original(EDIT, …) == "GRAIN_EDIT_20261004_201530_123_original.jpg"`、`edited(CAMERA, …) == "GRAIN_20261004_201530_123.jpg"`、`original(CAMERA, …) == "GRAIN_20261004_201530_123_original.jpg"`。
  - `queryPathsEndWithASlash`：`EDIT.queryPath == "Pictures/Grain Edits/"`、`CAMERA.queryPath == "Pictures/Grain/"`；`EDIT.alwaysSaveOriginal` 為 true、`CAMERA.alwaysSaveOriginal` 為 false。
  - `originalOfPairsOnlyEditedNames`：`originalOf("GRAIN_EDIT_x.jpg") == "GRAIN_EDIT_x_original.jpg"`；`originalOf("GRAIN_EDIT_x_original.jpg") == null`；`originalOf("note.png") == null`。
  - `isOriginalRecognizesTheSuffixOnly`：`isOriginal("GRAIN_EDIT_x_original.jpg")` 為 true；`isOriginal("GRAIN_EDIT_x.jpg")` 為 false；`isOriginal("GRAIN_EDIT_x_original (1).jpg")` 為 false（同名改名後不被當成原圖）。
- [ ] **Step 2: 確認失敗** — Run: `./gradlew :app:testDebugUnitTest --offline --tests 'tw.luma.camera.storage.PhotoNamesTest'`；Expected: 編譯失敗，`SaveTarget`、`PhotoNames` 未定義。
- [ ] **Step 3: 實作** `SaveTarget` 與 `PhotoNames` 於 `PhotoNames.kt`，套件 `tw.luma.camera.storage`；原圖後綴常數為 `"_original"`。
- [ ] **Step 4: 確認通過** — 同一指令；Expected: BUILD SUCCESSFUL，4 項通過。
- [ ] **Step 5: 提交** — add 上面兩個檔案，訊息 `Add edit save target and paired photo names`。

---

### Task 3: PhotoStorage 依去向存檔

**Files:**
- Modify: `app/src/main/java/tw/luma/camera/storage/PhotoStorage.kt`（`processAndSave`、`writePending`）、`app/src/main/java/tw/luma/camera/CameraViewModel.kt:233`、`app/src/main/java/tw/luma/camera/editor/PhotoEditorViewModel.kt:211,242`
- Test: `app/src/androidTest/java/tw/luma/camera/storage/PhotoStorageDeviceTest.kt`（新增；沿用 `androidTest/gl/OriginalLutDeviceTest.kt` 的 AndroidX Test 寫法）

**Interfaces:**
- Consumes: Task 2 的 `SaveTarget`、`PhotoNames`。
- Produces: `fun processAndSave(context: Context, source: File, filter: FilterSettings, target: SaveTarget, saveOriginal: Boolean = target.alwaysSaveOriginal): Uri`，回傳改完圖的 `Uri`；舊的 `Boolean` 第四參數簽章移除。

- [ ] **Step 1: 寫裝置測試（目前只能編譯）** — `PhotoStorageDeviceTest`，測試前自行在 cache 產生一張小 JPEG，測試後刪除寫入的所有 MediaStore 項目：
  - `editTargetWritesAPairToTheEditFolder`：以 `SaveTarget.EDIT` 存檔；斷言回傳項目的 `DISPLAY_NAME` 以 `GRAIN_EDIT_` 開頭、`RELATIVE_PATH == "Pictures/Grain Edits/"`；同資料夾存在名為 `PhotoNames.originalOf(name)` 的項目，其 bytes 與來源檔完全相同；`Pictures/Grain/` 的本 App 項目數不變。
  - `cameraTargetKeepsTheCameraFolderAndOptionalOriginal`：`SaveTarget.CAMERA, saveOriginal = false` 只新增一個 `Pictures/Grain/` 項目；`saveOriginal = true` 另有 `_original` 項目。
- [ ] **Step 2: 確認目前無法通過** — Run: `./gradlew :app:assembleDebugAndroidTest --offline`；Expected: 編譯失敗（`processAndSave` 沒有 `SaveTarget` 簽章）。
- [ ] **Step 3: 實作** 新簽章：檔名由 `PhotoNames` 以現有 `yyyyMMdd_HHmmss_SSS` 時間字串組成；`writePending(context, file, name, relativePath)` 的 `RELATIVE_PATH` 取自 `target.relativePath`；原圖副本沿用現有「成對 pending、全部成功才設 `IS_PENDING = 0`，失敗則刪除」流程。更新三個呼叫點：相機傳 `SaveTarget.CAMERA, snapshot.saveOriginal`，編輯兩處（單張、批次）傳 `SaveTarget.EDIT`。
- [ ] **Step 4: 確認編譯與單元測試** — Run: `./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline`；Expected: BUILD SUCCESSFUL；回報時註明裝置測試未執行。
- [ ] **Step 5: 提交** — add 上面列出的檔案，訊息 `Save edits to a separate folder with a copy of the original`。

---

### Task 4: 編輯成品查詢與配對

**Files:**
- Create: `app/src/main/java/tw/luma/camera/gallery/EditPairing.kt`
- Modify: `app/src/main/java/tw/luma/camera/gallery/GalleryViewModel.kt`（`GalleryItem`、`GalleryState`、`refresh`、新增編輯查詢）
- Test: `app/src/test/java/tw/luma/camera/gallery/EditPairingTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `SaveTarget.EDIT.queryPath`、`PhotoNames.isOriginal`、`PhotoNames.originalOf`。
- Produces:
  - `internal data class EditRow(val id: Long, val name: String)`，`internal data class EditPair(val edited: EditRow, val original: EditRow?)`。
  - `internal object EditPairing { fun pair(rows: List<EditRow>): List<EditPair> }`：保留輸入順序；列出所有非原圖項目；原圖由 `PhotoNames.originalOf(edited.name)` 在同一份 rows 中找名稱完全相同者，找不到為 `null`；沒有對應改完圖的原圖不輸出。
  - `GalleryItem` 末尾新增 `val original: Uri? = null`；`GalleryState` 新增 `val edits: List<GalleryItem> = emptyList()`。`refresh()` 一併載入，編輯項目 `video = false`、依 `DATE_ADDED DESC, _ID DESC` 排序。

- [ ] **Step 1: 寫失敗測試** — `EditPairingTest`（rows 用 `EditRow(id, name)`）：
  - `pairsEachEditedPhotoWithItsOriginal`：輸入 `[x_original, x, y_original, y]`（x、y 為 `GRAIN_EDIT_x.jpg`、`GRAIN_EDIT_y.jpg` 的完整名稱）→ 輸出順序為 x、y，各自 `original` 為對應原圖列。
  - `editedWithoutOriginalHasNoOriginal`：只有 `GRAIN_EDIT_x.jpg` → `original == null`。
  - `orphanOriginalIsNotListed`：只有 `GRAIN_EDIT_x_original.jpg` → 輸出為空。
  - `renamedDuplicateDoesNotPairWithAnotherEdit`：`[GRAIN_EDIT_x.jpg, GRAIN_EDIT_x (1).jpg, GRAIN_EDIT_x_original.jpg]` → 兩項都列出，只有 `GRAIN_EDIT_x.jpg` 配到原圖，`GRAIN_EDIT_x (1).jpg` 的 `original == null`。
  - `emptyFolderGivesAnEmptyList`：輸入空清單 → 輸出空清單。
- [ ] **Step 2: 確認失敗** — Run: `./gradlew :app:testDebugUnitTest --offline --tests 'tw.luma.camera.gallery.EditPairingTest'`；Expected: 編譯失敗，`EditPairing` 未定義。
- [ ] **Step 3: 實作** `EditPairing`；`GalleryViewModel` 新增編輯查詢，selection 與現有查詢一致（`RELATIVE_PATH = ? AND OWNER_PACKAGE_NAME = ? AND IS_PENDING = 0`，參數為 `SaveTarget.EDIT.queryPath` 與套件名），查到的 rows 經 `EditPairing.pair` 轉成 `GalleryItem`（`uri` 與 `original` 皆以 `ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)` 組成）；查詢失敗沿用現有「無法讀取相簿，請重試」。
- [ ] **Step 4: 確認通過** — Run: `./gradlew :app:testDebugUnitTest --offline`；Expected: BUILD SUCCESSFUL，全部 JVM 測試通過，其中 `EditPairingTest` 5 項。
- [ ] **Step 5: 提交** — add 上面三個檔案，訊息 `Query edited photos and pair them with their originals`。

---

### Task 5: 編輯 tab 首頁與成品檢視

**Files:**
- Create: `app/src/main/java/tw/luma/camera/ui/EditedHome.kt`
- Modify: `app/src/main/java/tw/luma/camera/ui/GalleryScreen.kt`（編輯 tab 空狀態分支約 `:141-148`；`selected`／`selectedIndex`／`GalleryViewer` 呼叫約 `:92,:106-116`）
- Test: `app/src/androidTest/java/tw/luma/camera/ui/CameraExperienceDeviceTest.kt`（新增一個測試）

**Interfaces:**
- Consumes: Task 4 的 `GalleryState.edits`、`GalleryItem.original`；既有 `GalleryThumbnail`（`GalleryScreen.kt:197`，目前 `private`，改為 `internal` 以供新檔使用）。
- Produces: `@Composable internal fun EditedHome(edits: List<GalleryItem>, model: GalleryViewModel, choosePhoto: () -> Unit, open: (GalleryItem) -> Unit)`：上方「選擇照片」按鈕（`testTag("editor-choose")`、沿用現有文字與圖示），下方在 `edits` 非空時顯示「編輯成品」標題與格子牆（`testTag("edited-grid")`，每格 `testTag("edited-item")`，內容描述 `"照片 ${item.name}"`）；`edits` 為空時只顯示按鈕。

- [ ] **Step 1: 寫裝置測試（只能編譯）** — `editedPhotosAppearOnTheEditTabHome`：在 `Pictures/Grain Edits` 以測試程式建立一對成品與原圖（沿用該檔 `:429` 附近的 MediaStore 插入寫法，`RELATIVE_PATH = "Pictures/Grain Edits"`）；開相簿、點 `gallery-edit-tab`；建立前先斷言 `edited-grid` 不存在而 `editor-choose` 顯示（資料夾空時只有按鈕）；建立後斷言 `edited-grid` 存在且 `edited-item` 恰有 1 個（`_original` 不列）；點開後 `gallery-pager` 顯示；返回後仍在編輯 tab；`gallery-photo-tab` 的格子牆不含該成品；結束刪除測試資料。
- [ ] **Step 2: 實作** — `GalleryScreen` 的 `selected` 旁新增 `var selectedEdit by rememberSaveable { mutableStateOf(false) }`；檢視頁清單為 `if (selectedEdit) state.edits else items`，`selectedIndex` 依該清單計算；點成品設 `selectedEdit = true`，點拍攝項目設 `false`；`GalleryViewer` 關閉時 `selected = null`。編輯 tab 尚未選照片時（現有 `importState.bitmap == null && !loading && error == null` 分支）改畫 `EditedHome`；已有草稿或批次時維持現有編輯畫面。
- [ ] **Step 3: 確認編譯與單元測試** — Run: `./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline`；Expected: BUILD SUCCESSFUL；裝置測試未執行。
- [ ] **Step 4: 提交** — add 上面三個檔案，訊息 `Show edited photos on the edit tab home`。

---

### Task 6: 檢視頁原圖／改完切換

**Files:**
- Modify: `app/src/main/java/tw/luma/camera/ui/GalleryScreen.kt`（`GalleryViewer`，頂部列 `:270-285` 與頁面 `:257-267`）
- Test: `app/src/androidTest/java/tw/luma/camera/ui/CameraExperienceDeviceTest.kt`（新增一個測試）

**Interfaces:**
- Consumes: Task 4 的 `GalleryItem.original`、Task 5 的成品檢視路徑。
- Produces: 檢視頁在 `!editing && current.original != null` 時，頂部右側顯示切換按鈕（`testTag("viewer-original-toggle")`，文字「看原圖」；顯示原圖時文字為「看改完」）；顯示原圖時，目前這一頁改以 `item.copy(uri = original, original = null)` 載入，其餘頁不受影響。

- [ ] **Step 1: 寫裝置測試（只能編譯）** — `viewerTogglesBetweenEditedAndOriginal`：以一對成品與原圖（兩者像素顏色不同的小 JPEG）開啟成品檢視；斷言 `viewer-original-toggle` 顯示文字「看原圖」，點擊後文字變為「看改完」；翻頁或返回再進入後文字恢復「看原圖」；另建一張沒有原圖的成品，斷言切換按鈕不存在。
- [ ] **Step 2: 實作** — `var showOriginal by rememberSaveable { mutableStateOf(false) }`，在 `currentUri` 改變時重設為 `false`；切換的是否可見只取決於 `current.original != null`；缺原圖或原圖解碼失敗時沿用 `GalleryPhoto` 既有「照片無法讀取」文字，切換按鈕仍可切回改完。縮放與位置因 `GalleryPhoto` 以 `item.uri` 為 key 而自然重設。
- [ ] **Step 3: 確認編譯** — Run: `./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline`；Expected: BUILD SUCCESSFUL；裝置測試未執行。
- [ ] **Step 4: 提交** — add 上面兩個檔案，訊息 `Let the viewer switch between an edit and its original`。

---

### Task 7: 相機縮圖不被取代、設定說明與既有裝置測試

**Files:**
- Modify: `app/src/main/java/tw/luma/camera/CameraViewModel.kt:246`（移除 `photoEdited`）、`app/src/main/java/tw/luma/camera/ui/CameraScreen.kt:122,596`、`app/src/androidTest/java/tw/luma/camera/ui/CameraExperienceDeviceTest.kt`（`ownPhotoCount` 與相關斷言，約 `:499,:573,:582,:660,:684,:698,:755`）

**Interfaces:**
- Consumes: Task 3 的 `SaveTarget.EDIT.queryPath`。
- Produces: `GalleryScreen` 的 `onSaved` 只重新整理相簿（`{ galleryModel.refresh() }`）；設定頁路徑說明為 `"照片：Pictures/Grain\n編輯：Pictures/Grain Edits\n影片：Movies/Grain"`；測試輔助 `ownPhotoCount(path: String = "Pictures/Grain/")`。

- [ ] **Step 1: 更新既有裝置測試（只能編譯）** — `ownPhotoCount` 加路徑參數；單張編輯測試改為：存檔後 `Pictures/Grain/` 數量不變、`Pictures/Grain Edits/` 增加 2（成品＋原圖），原圖 bytes 等於來源；批次測試部分失敗時 `Pictures/Grain Edits/` 增加 4（兩對），失敗那張不留任何檔案；重試後數量不變。測試結束清理新資料夾內的輸出。
- [ ] **Step 2: 實作** — 刪除 `CameraViewModel.photoEdited`；`CameraScreen:122` 的 `onSaved` 改為只呼叫 `galleryModel.refresh()`；設定頁說明文字照上面更新。
- [ ] **Step 3: 確認編譯與單元測試** — Run: `./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline`；Expected: BUILD SUCCESSFUL；若有任何 JVM 測試引用 `photoEdited` 則一併更新；裝置測試未執行。
- [ ] **Step 4: 提交** — add 上面三個檔案，訊息 `Keep the camera thumbnail for camera shots and document the edit folder`。

---

### Task 8: 版本、文件與完整驗證

**Files:**
- Modify: `app/build.gradle.kts`（`versionCode = 34`、`versionName = "0.7.1"`）、`README.md`（版本段落、成品路徑、新增 0.7.1 條目）
- Create: `docs/release-v071.md`

**Interfaces:**
- Consumes: Task 1 到 7 全部完成。

- [ ] **Step 1: 升版** — `versionCode` 33 改 34，`versionName` 改 `"0.7.1"`。
- [ ] **Step 2: 完整驗證** — Run: `./gradlew testDebugUnitTest lintPersonal assembleDebugAndroidTest assemblePersonal --offline`；Expected: BUILD SUCCESSFUL，JVM 測試 0 失敗（數量為先前 156 加上 `PhotoNamesTest` 4 項與 `EditPairingTest` 5 項），lint 0 錯誤。
- [ ] **Step 3: 驗證成品** — 以 `aapt2 dump badging` 確認 versionCode 34／versionName 0.7.1、非 debuggable；`apksigner verify --print-certs` 憑證 SHA-256 為 `e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8`；`zipalign -c -P 16 4` 通過；`python3 scripts/verify_personal_fuji_apk.py` 通過。
- [ ] **Step 4: 寫文件** — `docs/release-v071.md` 記錄：編輯成品獨立資料夾與成對原圖、編輯 tab 首頁、檢視頁切換、相機縮圖行為、存完離開編輯 tab 重置、驗證數字與「裝置測試只編譯、手機未驗證」；README 版本段落與路徑說明同步。
- [ ] **Step 5: 提交** — add `app/build.gradle.kts`、`README.md`、`docs/release-v071.md`，訊息 `Prepare Grain 0.7.1`。不推送、不建立 Release，發布需另行確認。
