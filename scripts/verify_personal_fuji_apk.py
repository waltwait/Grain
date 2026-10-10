#!/usr/bin/env python3
"""Verify a personal APK retains all ten unchanged, reviewed Fuji LUT assets."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

STYLES = {"CLASSIC-CHROME", "CLASSIC-Neg.", "REALA-ACE", "PROVIA", "Velvia",
          "ASTIA", "PRO-Neg.Std", "ETERNA", "ETERNA-BB", "ACROS"}
EXPECTED_FILES = {f"FLog2_to_{style}_33grid_V.1.00.cube" for style in STYLES}
REVIEWED_ZIP_SHA256 = "febfc7050999620651ca0cf162bf8b499970ef270b4da632765b61b958cf7940"
DIRECTORY = "assets/luts/fujifilm/"


def validate_assets(assets: dict[str, bytes]) -> None:
    files = {name for name in assets if name.endswith(".cube")}
    if files != EXPECTED_FILES:
        raise ValueError(f"Expected all ten Fuji LUTs; found {len(files)}. "
                         f"Missing: {', '.join(sorted(EXPECTED_FILES - files))}")
    manifest = json.loads(assets["sources.json"])
    if manifest.get("package_sha256") != REVIEWED_ZIP_SHA256:
        raise ValueError("Fuji source package differs from the reviewed personal pack")
    entries = manifest.get("files", [])
    if len(entries) != 10 or {entry["file"] for entry in entries} != EXPECTED_FILES:
        raise ValueError("Fuji source manifest is incomplete")
    for entry in entries:
        data = assets[entry["file"]]
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError(f"Fuji LUT bytes changed: {entry['file']}")
        if b"LUT_3D_SIZE 33" not in data:
            raise ValueError(f"Invalid Fuji LUT grid: {entry['file']}")


def verified_assets(apk_path: Path) -> dict[str, bytes]:
    with ZipFile(apk_path) as apk:
        assets = {name[len(DIRECTORY):]: apk.read(name) for name in apk.namelist()
                  if name.startswith(DIRECTORY) and (name.endswith(".cube") or name == DIRECTORY + "sources.json")}
    validate_assets(assets)
    return assets


def verify(apk_path: Path) -> None:
    verified_assets(apk_path)
    print(f"Verified all ten unchanged Fuji LUTs: {apk_path.name}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    try:
        verify(args.apk)
    except (ValueError, KeyError, OSError) as error:
        raise SystemExit(f"Personal Fuji APK verification failed: {error}")
