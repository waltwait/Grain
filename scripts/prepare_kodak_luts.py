#!/usr/bin/env python3
"""Convert the pinned Natron / Pat David Kodak Hald CLUT sources without resampling.

Source images and converted assets remain CC BY-SA 4.0. No grading changes are made.
"""

import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path
import numpy as np
from PIL import Image

REVISION = "af7b50d4caf6244fb6895a647f5b6a84efe7931a"
PROFILES = (
    ("portra-160", "Portra 160", "negative_new/kodak_portra_160.png", "d3095026f64dcdd8eb7f307409e81e1327e2a1fe"),
    ("portra-400", "Portra 400", "negative_new/kodak_portra_400.png", "0c6d3ec8f7ab1da1619c573fcab5ff81260701cb"),
    ("portra-800", "Portra 800", "negative_new/kodak_portra_800.png", "37635d8e433bf10a7dec0e287cdcd4e0c243d78f"),
    ("ektachrome-100-vs", "Ektachrome 100 VS", "colorslide/kodak_ektachrome_100_vs.png", "12afe2f3f8cc677b26a192a30a2837faf0639f89"),
    ("tri-x-400", "Tri-X 400", "bw/kodak_tri-x_400.png", "89558286f94f946840f8b94503a7a24d048c42e2"),
)


def hald_to_cube(image):
    side, height = image.size
    level = round(side ** (1.0 / 3.0))
    grid = level * level
    if side != height or level ** 3 != side or not 2 <= grid <= 65 or image.mode != "RGB":
        raise ValueError("Expected square RGB Hald CLUT with 2-65 channel samples")
    # Pixel order in a Hald is red-fastest, then green, then blue, exactly like CUBE.
    return np.asarray(image, dtype=np.uint8).reshape(grid, grid, grid, 3).copy()


def export(root, check=False, cubes=False):
    source = root / "third_party/luts/natron"
    destination = root / "app/src/main/assets/luts/kodak"
    if not check:
        destination.mkdir(parents=True, exist_ok=True)
    exported = root / "output/luts/kodak-emulations-v044"
    if cubes:
        exported.mkdir(parents=True, exist_ok=True)
    manifest = []
    for slug, title, upstream_path, expected_blob in PROFILES:
        file = source / Path(upstream_path).name
        raw = file.read_bytes()
        actual_blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        if actual_blob != expected_blob:
            raise ValueError(f"Changed upstream source: {file}")
        with Image.open(file) as image:
            table = hald_to_cube(image)
        blob = b"GRAINLUT" + struct.pack(">II", 2, table.shape[0]) + table.tobytes()
        packed = destination / f"{slug}.glut"
        if check:
            if not packed.exists() or packed.read_bytes() != blob:
                raise SystemExit(f"Stale or missing Kodak table: {packed}")
        else:
            packed.write_bytes(blob)
        if cubes:
            with (exported / f"Kodak-{title.replace(' ', '-')}-Emulation-sRGB-64.cube").open("w", encoding="utf-8") as cube:
                cube.write(f'# Community film emulation by Pat David, distributed by Natron HaldCLUT.\n'
                           f'# License: CC BY-SA 4.0 | https://creativecommons.org/licenses/by-sa/4.0/\n'
                           f'# Source: https://github.com/NatronGitHub/clut/blob/{REVISION}/{upstream_path}\n'
                           f'# Grain conversion: Hald RGB samples to CUBE; no resampling or grading changes.\n'
                           f'# Not Kodak official. Input/output: sRGB SDR. See ATTRIBUTION.txt and upstream README.\n'
                           f'TITLE "Kodak {title} (Community Emulation)"\nLUT_3D_SIZE 64\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n')
                normalized = table.astype(np.float32) / 255.0
                for pixel in normalized.reshape(-1, 3):
                    cube.write(" ".join(format(float(channel), ".9g") for channel in pixel) + "\n")
        manifest.append({"id": f"kodak-{slug}", "title": title, "grid": table.shape[0],
                         "author": "Pat David", "license": "CC BY-SA 4.0",
                         "source": f"https://github.com/NatronGitHub/clut/blob/{REVISION}/{upstream_path}",
                         "source_sha256": hashlib.sha256(raw).hexdigest(),
                         "asset_sha256": hashlib.sha256(blob).hexdigest(),
                         "conversion": "Original 8-bit RGB samples repacked without resampling or grading changes",
                         "input": "sRGB SDR (RawTherapee film-simulation collection convention)", "output": "sRGB SDR"})
    if not check:
        (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        for name in ("README.md", "ATTRIBUTION.txt"):
            (destination / name).write_bytes((source / name).read_bytes())
        from generate_grain_originals import sample_table, study_image, font
        from PIL import ImageDraw
        study = study_image()
        sheet = Image.new("RGB", (1260, 830), "#121310")
        draw = ImageDraw.Draw(sheet)
        draw.text((32, 24), "KODAK FILM EMULATIONS", font=font(30), fill="#ead7a3")
        draw.text((32, 70), "Existing looks by Pat David / Natron HaldCLUT / CC BY-SA 4.0", font=font(17), fill="#cac9c3")
        frames = [("Original", study)]
        for slug, title, upstream_path, _ in PROFILES:
            with Image.open(source / Path(upstream_path).name) as image:
                table = hald_to_cube(image).astype(np.float32) / 255.0
            frames.append((title, sample_table(table, study)))
        for i, (title, colors) in enumerate(frames):
            x, y = 32 + (i % 3) * 410, 115 + (i // 3) * 336
            sheet.paste(Image.fromarray(np.round(np.clip(colors, 0.0, 1.0) * 255).astype(np.uint8)), (x, y))
            draw.text((x, y + 290), title, font=font(21), fill="#f3f1eb")
        draw.text((32, 793), "100% / sRGB / synthetic color study / community approximations; not Kodak official LUTs", font=font(15), fill="#aeadA5")
        sheet.save(root / "output/design/kodak-emulations-v044-preview.png")
        if cubes:
            with zipfile.ZipFile(root / "output/Grain-Kodak-Emulations-v1-sRGB-LUTs.zip", "w", zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(exported.glob("*.cube")):
                    archive.write(path, path.name)
                for name in ("README.md", "ATTRIBUTION.txt"):
                    archive.write(source / name, name)
    print(json.dumps({"looks": len(manifest), "source_samples": 64 ** 3 * 3 * len(manifest), "check": check}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--cubes", action="store_true", help="Also export a standalone CUBE pack with attribution")
    args = parser.parse_args()
    if args.check and args.cubes:
        parser.error("--check and --cubes are mutually exclusive")
    export(args.root, args.check, args.cubes)
