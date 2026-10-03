"use strict";

const version = document.getElementById("version");
const notes = document.getElementById("notes");
const download = document.getElementById("download");
const status = document.getElementById("status");
const retry = document.getElementById("retry");

async function loadRelease() {
  download.hidden = true;
  download.removeAttribute("href");
  retry.hidden = true;
  notes.textContent = "";
  status.textContent = "";
  version.textContent = "正在取得最新版…";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch("latest.json", { cache: "no-store", signal: controller.signal });
    if (!response.ok) throw new Error("release unavailable");
    const info = await response.json();
    const url = new URL(info.apkUrl);
    if (info.schemaVersion !== 1 || info.packageName !== "tw.luma.camera" ||
        !Number.isInteger(info.versionCode) || info.versionCode <= 0 || info.versionCode > 2147483647 ||
        typeof info.versionName !== "string" || !info.versionName.trim() || info.versionName.length > 64 ||
        !Number.isInteger(info.apkSize) || info.apkSize <= 0 || info.apkSize > 100 * 1024 * 1024 ||
        !Number.isInteger(info.minSdk) || info.minSdk < 1 || info.minSdk > 999 ||
        !Number.isInteger(info.bundledFujiCount) || info.bundledFujiCount < 0 || info.bundledFujiCount > 10 ||
        !["release", "personal-fuji"].includes(info.channel) ||
        (info.channel === "personal-fuji" && info.bundledFujiCount !== 10) ||
        (info.access !== undefined && info.access !== "github-login") ||
        !/^[a-f0-9]{64}$/i.test(info.apkSha256) || !/^[a-f0-9]{64}$/i.test(info.signingCertificateSha256) ||
        (info.notes !== undefined && (typeof info.notes !== "string" || info.notes.length > 4000)) ||
        url.protocol !== "https:" || url.username || url.password || url.hash) {
      throw new Error("invalid release");
    }
    version.textContent = "Grain " + info.versionName;
    notes.textContent = info.notes || "";
    download.href = info.access === "github-login"
      ? "https://github.com/waltwait/Grain/releases/latest" : url.href;
    download.hidden = false;
    status.textContent = "Android " + (info.minSdk === 29 ? "10" : "API " + info.minSdk) + "+ · " + (info.apkSize / 1024 / 1024).toFixed(1) + " MB";
    if (info.access === "github-login") status.textContent += " · 登入 GitHub 下載";
  } catch (_) {
    version.textContent = "Grain";
    status.textContent = "暫時無法取得最新版，請稍後再試。";
    retry.hidden = false;
  } finally { clearTimeout(timeout); }
}

retry.addEventListener("click", loadRelease);
loadRelease();
