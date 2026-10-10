#!/usr/bin/env python3
"""Restore the existing public Fuji pack; never add binary assets to Git."""
import argparse
import hashlib
from pathlib import Path
import urllib.request

from verify_personal_fuji_apk import EXPECTED_FILES, validate_assets, verified_assets

ROOT = Path(__file__).resolve().parents[1]
BASELINE_URL = "https://github.com/waltwait/Grain/releases/download/v0.7.8/Grain-0.7.8-personal-fuji.apk"
BASELINE_SHA256 = "bf20c3cc63dc1cf4606b1b8136013f84fbeaceebf66bddde044e7c8ba6b0d35c"
DESTINATION = ROOT / "app/src/debug/assets/luts/fujifilm"


def restore(apk: Path, destination: Path) -> None:
    if hashlib.sha256(apk.read_bytes()).hexdigest() != BASELINE_SHA256:
        raise ValueError("Fuji baseline APK checksum mismatch")
    assets = verified_assets(apk)
    if destination.exists() and any(destination.iterdir()):
        current = {p.name: p.read_bytes() for p in destination.iterdir() if p.is_file()}
        validate_assets(current)
        if any(current.get(name) != data for name, data in assets.items()):
            raise ValueError("Existing Fuji assets differ; refusing to overwrite them")
        return
    destination.mkdir(parents=True, exist_ok=True)
    for name, data in assets.items():
        if name not in EXPECTED_FILES | {"sources.json"}:
            raise ValueError("Unexpected asset filename")
        (destination / name).write_bytes(data)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-apk", type=Path, help="Use the verified 0.7.8 APK already on disk")
    parser.add_argument("--destination", type=Path, default=DESTINATION)
    args = parser.parse_args()
    if args.source_apk is None and args.destination.exists() and any(args.destination.iterdir()):
        validate_assets({p.name: p.read_bytes() for p in args.destination.iterdir() if p.is_file()})
        print("Existing ten Fuji LUTs verified; no download needed.")
        return
    apk = args.source_apk or ROOT / ".tools/fuji-baseline/Grain-0.7.8.apk"
    if not apk.exists():
        if args.source_apk:
            raise ValueError("Source APK does not exist")
        apk.parent.mkdir(parents=True, exist_ok=True)
        pending = apk.with_suffix(".part")
        try:
            request = urllib.request.Request(BASELINE_URL, headers={"User-Agent": "Grain-build"})
            with urllib.request.urlopen(request, timeout=90) as response, pending.open("wb") as output:
                size = 0
                while block := response.read(1024 * 1024):
                    size += len(block)
                    if size > 100 * 1024 * 1024:
                        raise ValueError("Baseline download exceeds the APK size limit")
                    output.write(block)
            if hashlib.sha256(pending.read_bytes()).hexdigest() != BASELINE_SHA256:
                raise ValueError("Downloaded baseline checksum mismatch")
            pending.replace(apk)
        finally:
            pending.unlink(missing_ok=True)
    restore(apk, args.destination)
    print("Restored and verified all ten unchanged Fuji LUTs from public Grain 0.7.8.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        raise SystemExit(f"Fuji restoration failed: {error}")
