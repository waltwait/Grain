#!/usr/bin/env python3
"""Prepare a website and latest.json from a verified Grain APK; never upload files."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import urlsplit
from zipfile import ZipFile

from verify_personal_fuji_apk import DIRECTORY, verify as verify_fuji

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_CERTIFICATE = "e6c756c9525fbdad8035a1beb79eed96eb53243e9b537e3be94ab46c0c1217c8"


def https_url(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.isascii() or any(c.isspace() for c in value) or parsed.username is not None or parsed.password is not None or parsed.fragment:
        raise ValueError("APK URL must use HTTPS without embedded credentials or a fragment")
    if parsed.port is not None and not 1 <= parsed.port <= 65535:
        raise ValueError("APK URL port is invalid")
    return value


def run(command: list[str]) -> str:
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def manifest(apk: Path, apk_url: str, notes: str, build_tools: Path) -> dict:
    https_url(apk_url)
    if len(notes) > 4000:
        raise ValueError("Release notes exceed 4000 characters")
    badging = run([str(build_tools / "aapt2"), "dump", "badging", str(apk)])
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    sdk = re.search(r"^minSdkVersion:'(\d+)'", badging, re.M)
    if not package or not sdk or package[1] != "tw.luma.camera" or "application-debuggable" in badging:
        raise ValueError("APK must be a non-debuggable Grain build")
    if not 1 <= int(package[2]) <= 2147483647 or len(package[3]) > 64 or not 1 <= int(sdk[1]) <= 999:
        raise ValueError("APK version metadata is invalid")
    signature = run([str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)])
    certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$", signature, re.M)
    if len(certificates) != 1 or certificates[0].lower() != PRODUCTION_CERTIFICATE:
        raise ValueError("APK does not use the existing Grain production signing certificate")
    with ZipFile(apk) as archive:
        fuji_count = sum(name.startswith(DIRECTORY) and name.endswith(".cube") for name in archive.namelist())
    if fuji_count:
        verify_fuji(apk)
    size = apk.stat().st_size
    if not 1 <= size <= 100 * 1024 * 1024:
        raise ValueError("APK size exceeds the updater limit")
    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"schemaVersion": 1, "packageName": package[1], "versionName": package[3],
            "versionCode": int(package[2]), "minSdk": int(sdk[1]),
            "channel": "personal-fuji" if fuji_count else "release", "bundledFujiCount": fuji_count,
            "apkUrl": apk_url, "apkSize": size, "apkSha256": digest.hexdigest(),
            "signingCertificateSha256": certificates[0].lower(), "notes": notes}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apk-url", required=True, help="Actual HTTPS location of this version's APK")
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True, help="Android SDK build-tools directory")
    parser.add_argument("--output", type=Path, default=ROOT / "output/update-site")
    args = parser.parse_args()
    try:
        notes = args.notes_file.read_text(encoding="utf-8").strip() if args.notes_file else ""
        info = manifest(args.apk, args.apk_url, notes, args.build_tools)
        directory = args.output
        directory.mkdir(parents=True, exist_ok=True)
        # Keep versioned APK URLs stable while latest.json changes for subsequent releases.
        filename = "Grain-" + str(info["versionCode"]) + ".apk"
        pending = directory / (filename + ".part")
        try:
            shutil.copyfile(args.apk, pending)
            os.replace(pending, directory / filename)
        finally:
            pending.unlink(missing_ok=True)
        for file in ("index.html", "app.js", "style.css"):
            shutil.copyfile(ROOT / "website" / file, directory / file)
        pending_json = directory / "latest.json.part"
        pending_json.write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        os.replace(pending_json, directory / "latest.json")
        print(f"Prepared {info['versionName']} / code {info['versionCode']} / {info['channel']} in {directory}")
        print(f"APK filename: {filename}. Ensure --apk-url points to this file after deployment.")
        print("Local files only. No website or APK has been published.")
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Website preparation failed: {error}")


if __name__ == "__main__":
    main()
