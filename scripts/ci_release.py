#!/usr/bin/env python3
"""Verified, resumable Grain release and update-feed publication for Actions."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

from prepare_update_site import manifest

ROOT = Path(__file__).resolve().parents[1]
REPO = "waltwait/Grain"
WORK = ROOT / ".tools/release"
FEED = Path("updates/personal-fuji/latest.json")


def version(source: str) -> tuple[str, int]:
    names = re.findall(r'^\s*versionName = "(\d+\.\d+\.\d+)"\s*$', source, re.M)
    codes = re.findall(r'^\s*versionCode = (\d+)\s*$', source, re.M)
    if len(names) != 1 or len(codes) != 1 or not 1 <= int(codes[0]) <= 2147483647:
        raise ValueError("Expected exactly one valid Grain versionName and versionCode")
    return names[0], int(codes[0])


def filename(name: str) -> str:
    return f"Grain-{name}-personal-fuji.apk"


def apk_url(name: str) -> str:
    return f"https://github.com/{REPO}/releases/download/v{name}/{filename(name)}"


def run(args: list[str], **kwargs) -> str:
    return subprocess.run(args, cwd=ROOT, check=True, capture_output=True, text=True, **kwargs).stdout.strip()


def api(path: str):
    return json.loads(run(["gh", "api", f"repos/{REPO}/{path}"]))


def releases():
    return api("releases?per_page=100")


def release_for(name: str):
    matches = [item for item in releases() if item["tag_name"] == f"v{name}"]
    if len(matches) > 1:
        raise ValueError("Multiple releases for one version")
    return matches[0] if matches else None


def validate_asset(release: dict, info: dict, published: bool) -> None:
    if release.get("prerelease") or release.get("tag_name") != "v" + info["versionName"]:
        raise ValueError("Unexpected release version or prerelease")
    if published and release.get("draft"):
        raise ValueError("Release is still a draft")
    assets = release.get("assets", [])
    if len(assets) != 1:
        raise ValueError("A Grain release must contain exactly one APK")
    asset = assets[0]
    if (asset.get("name") != filename(info["versionName"]) or asset.get("state") != "uploaded"
            or asset.get("size") != info["apkSize"] or asset.get("digest") != "sha256:" + info["apkSha256"]):
        raise ValueError("Release APK name, state, size or SHA-256 differs from the verified build")
    if published and asset.get("browser_download_url") != info["apkUrl"]:
        raise ValueError("Public APK URL does not match the update feed")


def require_increasing(info: dict, previous: dict) -> None:
    if info["versionCode"] < previous["versionCode"]:
        raise ValueError("Refusing to downgrade the public update feed")
    if info["versionCode"] == previous["versionCode"] and info != previous:
        raise ValueError("Refusing to replace an already-published versionCode")


def tag_commit(name: str) -> str:
    obj = api(f"git/ref/tags/v{name}")["object"]
    for _ in range(5):
        if obj["type"] == "commit":
            return obj["sha"]
        if obj["type"] != "tag":
            break
        obj = api("git/tags/" + obj["sha"])["object"]
    raise ValueError("Release tag does not point to a commit")


def require_tag_source(name: str, published: bool) -> None:
    commit = tag_commit(name)
    if published:
        run(["git", "merge-base", "--is-ancestor", commit, os.environ["GITHUB_SHA"]])
    elif commit != os.environ["GITHUB_SHA"]:
        raise ValueError("Existing draft belongs to another source commit; inspect it before retrying")


def status() -> None:
    name, code = version((ROOT / "app/build.gradle.kts").read_text())
    previous = json.loads((ROOT / FEED).read_text())
    release = release_for(name)
    mode = "build"
    if release and not release["draft"]:
        if code == previous["versionCode"] and name == previous["versionName"]:
            validate_asset(release, previous, published=True)
            mode = "skip"
        else:
            mode = "resume"
    elif release and release.get("assets"):
        if len(release["assets"]) != 1 or release["assets"][0].get("state") != "uploaded":
            raise ValueError("Incomplete draft upload; inspect or remove the draft before retrying")
        mode = "resume"
    dry_run = os.environ.get("GRAIN_DRY_RUN") == "true"
    if dry_run:
        mode = "build"
    if not dry_run and mode != "skip" and code <= previous["versionCode"]:
        raise ValueError("Increase versionName and versionCode before publishing a new build")
    if mode == "resume":
        require_tag_source(name, published=not release["draft"])
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "state.json").write_text(json.dumps({"versionName": name, "versionCode": code, "mode": mode}))
    print(f"name={name}\ncode={code}\nmode={mode}")


def verify() -> None:
    state = json.loads((WORK / "state.json").read_text())
    name, code = state["versionName"], state["versionCode"]
    target = WORK / filename(name)
    if state["mode"] == "resume":
        run(["gh", "release", "download", f"v{name}", "--repo", REPO, "--pattern", filename(name), "--dir", str(WORK), "--clobber"])
        apk = target
    else:
        apk = ROOT / "app/build/outputs/apk/personal/app-personal.apk"
        reports = list((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
        tests = 0
        for path in reports:
            suite = ET.parse(path).getroot()
            if any(int(suite.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
                raise ValueError("JVM tests failed, errored or were skipped")
            tests += int(suite.attrib.get("tests", 0))
        if not tests:
            raise ValueError("No successful JVM test reports")
        lint = ET.parse(ROOT / "app/build/reports/lint-results-personal.xml").getroot()
        if any(issue.attrib["severity"] in {"Error", "Fatal"} for issue in lint.findall("issue")):
            raise ValueError("Personal lint contains errors")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    notes_file = ROOT / f"docs/releases/{name}.md"
    if not notes_file.is_file():
        raise ValueError(f"Missing release notes: docs/releases/{name}.md")
    notes = notes_file.read_text().strip()
    info = manifest(apk, apk_url(name), notes, tools)
    if info["versionName"] != name or info["versionCode"] != code or info["bundledFujiCount"] != 10 or info["channel"] != "personal-fuji":
        raise ValueError("APK version or Fuji channel mismatch")
    run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk)])
    if apk.resolve() != target.resolve():
        shutil.copyfile(apk, target)
    if state["mode"] == "resume":
        validate_asset(release_for(name), info, published=not release_for(name)["draft"])
    (WORK / "latest.json").write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n")
    (WORK / "notes.md").write_text(notes + "\n\n可直接覆蓋更新；原正式簽章與十款富士 LUT 已核對。\n")
    print(f"Verified Grain {name}: production signer, ten Fuji LUTs, non-debuggable, ZIP alignment; SHA-256 {info['apkSha256']}")


def update_feed(info: dict) -> None:
    # Only the generated feed is committed. Retry on a concurrent main update without overwriting it.
    run(["git", "config", "user.name", "github-actions[bot]"])
    run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"])
    for _ in range(3):
        run(["git", "fetch", "origin", "main"])
        run(["git", "checkout", "--detach", "FETCH_HEAD"])
        previous = json.loads((ROOT / FEED).read_text())
        require_increasing(info, previous)
        if previous == info:
            return
        (ROOT / FEED).write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n")
        run(["git", "add", str(FEED)])
        run(["git", "commit", "-m", f"Update Grain {info['versionName']} download feed"])
        result = subprocess.run(["git", "push", "origin", "HEAD:main"], cwd=ROOT, capture_output=True, text=True)
        if result.returncode == 0:
            return
    raise ValueError("Release is public but updating the feed failed; rerun this workflow to finish")


def publish() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_REPOSITORY") != REPO or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Publication is restricted to this repository's main Actions workflow")
    info = json.loads((WORK / "latest.json").read_text())
    require_increasing(info, json.loads((ROOT / FEED).read_text()))
    name = info["versionName"]
    release = release_for(name)
    if release is None:
        # Draft creation does not guarantee a tag exists yet. Create it explicitly in Actions.
        reference = run(["git", "ls-remote", "--tags", "origin", f"refs/tags/v{name}"])
        if reference:
            require_tag_source(name, published=False)
        else:
            run(["git", "config", "user.name", "github-actions[bot]"])
            run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"])
            run(["git", "tag", "-a", f"v{name}", os.environ["GITHUB_SHA"], "-m", f"Grain {name}"])
            run(["git", "push", "origin", f"refs/tags/v{name}"])
        run(["gh", "release", "create", f"v{name}", str(WORK / filename(name)), "--repo", REPO,
             "--verify-tag", "--draft", "--title", f"Grain {name}", "--notes-file", str(WORK / "notes.md")])
        release = release_for(name)
    elif release["draft"] and not release["assets"]:
        require_tag_source(name, published=False)
        run(["gh", "release", "upload", f"v{name}", str(WORK / filename(name)), "--repo", REPO])
        release = release_for(name)
    validate_asset(release, info, published=not release["draft"])
    require_tag_source(name, published=not release["draft"])
    if release["draft"]:
        run(["gh", "release", "edit", f"v{name}", "--repo", REPO, "--draft=false", "--latest"])
    release = release_for(name)
    validate_asset(release, info, published=True)
    if api("releases/latest")["tag_name"] != f"v{name}":
        raise ValueError("Published release is not GitHub's latest; refusing to update the feed")
    request = urllib.request.Request(info["apkUrl"], method="HEAD", headers={"User-Agent": "Grain-release-verifier"})
    with urllib.request.urlopen(request, timeout=90) as response:
        if response.status != 200:
            raise ValueError("Public APK download is unavailable")
    update_feed(info)
    print(f"Published https://github.com/{REPO}/releases/tag/v{name} and updated the App/Pages feed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("status", "verify", "publish"))
    args = parser.parse_args()
    try:
        {"status": status, "verify": verify, "publish": publish}[args.command]()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Grain release failed: {error}")
