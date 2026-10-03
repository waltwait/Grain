// Website metadata validation runs without a browser or network.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../website/app.js"), "utf8");
const valid = { schemaVersion: 1, packageName: "tw.luma.camera", versionName: "0.6.2", versionCode: 25,
  minSdk: 29, channel: "personal-fuji", bundledFujiCount: 10,
  apkUrl: "https://updates.example.test/Grain-25.apk", apkSize: 14000000,
  apkSha256: "a".repeat(64), signingCertificateSha256: "b".repeat(64), notes: "新版" };

async function load(info, ok = true) {
  const elements = Object.fromEntries(["version", "notes", "download", "status", "retry"].map(id => [id, {
    textContent: "", hidden: false, href: null, removeAttribute(name) { this[name] = null; },
    addEventListener() {},
  }]));
  vm.runInNewContext(source, { document: { getElementById: id => elements[id] },
    fetch: async () => ({ ok, json: async () => info }), URL, AbortController, setTimeout, clearTimeout });
  await new Promise(setImmediate);
  return elements;
}

test("shows exactly the current APK and renders notes as text", async () => {
  const elements = await load({ ...valid, notes: "<img src=x onerror=alert(1)>" });
  assert.equal(elements.download.hidden, false);
  assert.equal(elements.download.href, valid.apkUrl);
  assert.equal(elements.version.textContent, "Grain 0.6.2");
  assert.equal(elements.notes.textContent, "<img src=x onerror=alert(1)>");
  assert.equal(elements.retry.hidden, true);
});

test("does not offer APKs from malformed or incompatible metadata", async () => {
  const cases = [{ schemaVersion: 2 }, { packageName: "other.app" }, { versionCode: "25" },
    { apkSize: 200000000 }, { apkSha256: "invalid" }, { bundledFujiCount: 0 },
    { notes: {} }, { minSdk: undefined }, { versionName: 25 }, { versionCode: 2147483648 }];
  for (const change of cases) {
    const elements = await load({ ...valid, ...change });
    assert.equal(elements.download.hidden, true, JSON.stringify(change));
    assert.equal(elements.download.href, null);
    assert.equal(elements.retry.hidden, false);
  }
});

test("rejects unsafe download URLs", async () => {
  for (const apkUrl of ["http://updates.example.test/app.apk", "javascript:alert(1)",
    "https://owner:secret@updates.example.test/app.apk", "https://updates.example.test/app.apk#fragment"]) {
    assert.equal((await load({ ...valid, apkUrl })).download.hidden, true);
  }
});

test("failed requests keep the download disabled and expose retry", async () => {
  const elements = await load(valid, false);
  assert.equal(elements.download.hidden, true);
  assert.equal(elements.retry.hidden, false);
  assert.match(elements.status.textContent, /暫時無法/);
});

test("private downloads open the latest release rather than the APK asset", async () => {
  const elements = await load({ ...valid, access: "github-login" });
  assert.equal(elements.download.hidden, false);
  assert.equal(elements.download.href, "https://github.com/waltwait/Grain/releases/latest");
  assert.match(elements.status.textContent, /登入 GitHub 下載/);
  assert.equal((await load({ ...valid, access: "unknown" })).download.hidden, true);
});
