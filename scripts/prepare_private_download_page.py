#!/usr/bin/env python3
"""Prepare public page files for a private Grain release; never copy or upload the APK."""
import argparse
import json
from pathlib import Path
import shutil

from prepare_update_site import ROOT, manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "output/private-download-page")
    args = parser.parse_args()
    notes = args.notes_file.read_text(encoding="utf-8").strip() if args.notes_file else ""
    info = manifest(args.apk, args.apk_url, notes, args.build_tools)
    if info["channel"] != "personal-fuji" or info["bundledFujiCount"] != 10:
        raise SystemExit("The private page requires the verified personal Fuji build.")
    files = ("index.html", "app.js", "style.css")
    allowed = {*files, "latest.json", ".nojekyll"}
    directory = args.output
    directory.mkdir(parents=True, exist_ok=True)
    if any(path.name not in allowed or not path.is_file() or path.is_symlink() for path in directory.iterdir()):
        raise SystemExit("Output contains unexpected files. Use a directory containing only page files.")
    info["access"] = "github-login"
    for name in files:
        shutil.copyfile(ROOT / "website" / name, directory / name)
    (directory / "latest.json").write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (directory / ".nojekyll").touch()
    print(f"Prepared public page for {info['versionName']}: {', '.join(sorted(allowed))}")
    print("No APK, LUT, signing key or account credential was copied. Local files only.")


if __name__ == "__main__":
    main()
