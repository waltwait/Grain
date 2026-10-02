#!/usr/bin/env python3
"""Author Grain Originals, export exact float32 tables / CUBEs and a synthetic color study.

Requires numpy and Pillow. All transforms and preview colors are authored in this project.
Run from any directory; defaults to the repository containing this script.
"""

import argparse
import hashlib
import json
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

SIZE = 33
LUMA = np.array([0.2126, 0.7152, 0.0722])


@dataclass(frozen=True)
class Look:
    slug: str
    title: str
    contrast: float
    black: float
    white: float
    saturation: float = 1.0
    green_hue: float = 0.0
    blue_hue: float = 0.0
    green_sat: float = 1.0
    blue_sat: float = 1.0
    shadow: tuple = (0.0, 0.0, 0.0)
    highlight: tuple = (0.0, 0.0, 0.0)
    neon_reduction: float = 0.0


LOOKS = (
    Look("daylight", "Daylight", .16, .010, .985, .94, -.012, -.012, .94, .98,
         (-.002, .001, .003), (.008, .003, -.005)),
    Look("warm-portrait", "Warm Portrait", .08, .018, .982, .91, -.012, -.006, .87, .90,
         (.004, .000, -.006), (.018, .006, -.012)),
    Look("chrome-street", "Chrome Street", .40, .025, .975, .78, -.035, -.010, .84, .94,
         (-.012, .001, .015), (.010, .004, -.007)),
    Look("golden-hour", "Golden Hour", .22, .014, .982, 1.03, -.023, -.005, .96, .94,
         (.009, .001, -.010), (.034, .009, -.022)),
    Look("night-cinema", "Night Cinema", .44, .010, .970, .86, -.008, -.024, .86, .94,
         (-.025, .009, .017), (.019, .004, -.013), .14),
    Look("silver", "Silver", .48, .012, .980),
)


def smoothstep(lo, hi, value):
    t = np.clip((value - lo) / (hi - lo), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def tone(value, look):
    # Analytic derivative stays positive for these contrast parameters. The toe lifts
    # black gently; the shoulder ends below white without crushing already-clipped data.
    curved = value + look.contrast * value * (1.0 - value) * (2.0 * value - 1.0)
    return look.black + (look.white - look.black) * curved


def rgb_to_hsv(rgb):
    high, low = rgb.max(axis=-1), rgb.min(axis=-1)
    span = high - low
    safe_span = np.where(span > 1e-12, span, 1.0)
    r, g, b = np.moveaxis(rgb, -1, 0)
    hue = np.where(high == r, (g - b) / safe_span,
                   np.where(high == g, (b - r) / safe_span + 2.0, (r - g) / safe_span + 4.0))
    hue = np.where(span > 1e-12, (hue / 6.0) % 1.0, 0.0)
    saturation = span / np.where(high > 1e-12, high, 1.0)
    return hue, saturation, high


def hsv_to_rgb(h, s, v):
    # Triangle representation is continuous across the cyclic hue seam.
    phase = (h[..., None] * 6.0 + np.array([0.0, 4.0, 2.0])) % 6.0
    primary = np.clip(np.abs(phase - 3.0) - 1.0, 0.0, 1.0)
    return v[..., None] * (1.0 - s[..., None] + s[..., None] * primary)


def hue_band(hue, center, width, saturation):
    distance = ((hue - center + .5) % 1.0) - .5
    return np.exp(-.5 * (distance / width) ** 2) * smoothstep(.07, .22, saturation)


def grade(rgb, look):
    rgb = np.asarray(rgb, dtype=np.float64)
    if look.slug == "silver":
        # A mild yellow-filter interpretation, baked into a monochrome SDR tone curve.
        light = rgb @ np.array([.26, .65, .09])
        return np.repeat(tone(light, look)[..., None], 3, axis=-1)

    light = rgb @ LUMA
    mapped_light = tone(light, look)
    hue, saturation, value = rgb_to_hsv(rgb)
    orange = hue_band(hue, .075, .060, saturation)
    green = hue_band(hue, 1.0 / 3.0, .085, saturation)
    blue = hue_band(hue, .62, .085, saturation)
    hue_shift = (look.green_hue * green + look.blue_hue * blue) * (1.0 - .8 * orange)
    palette = hsv_to_rgb((hue + hue_shift) % 1.0, saturation, value)
    chroma = palette - (palette @ LUMA)[..., None]

    # Orange hue protection is a color-range adjustment, not face/skin segmentation.
    local_sat = look.saturation + (1.0 - look.saturation) * .65 * orange
    local_sat *= 1.0 + (look.green_sat - 1.0) * green + (look.blue_sat - 1.0) * blue
    local_sat *= 1.0 - look.neon_reduction * smoothstep(.45, .90, saturation) * (1.0 - orange)
    chroma *= local_sat[..., None]

    envelope = 4.0 * light * (1.0 - light)
    shadows = (1.0 - smoothstep(.12, .58, light)) * envelope
    highlights = smoothstep(.45, .92, light) * envelope
    tint = shadows[..., None] * np.array(look.shadow) + highlights[..., None] * np.array(look.highlight)
    tint -= (tint @ LUMA)[..., None]
    chroma += tint * (1.0 - .45 * orange[..., None])

    # Smooth compression into the available RGB gamut avoids hard channel clipping.
    positive = chroma.max(axis=-1) / (1.0 - mapped_light)
    negative = -chroma.min(axis=-1) / mapped_light
    pressure = np.maximum(positive, negative)
    compression = (1.0 + pressure ** 8) ** (-1.0 / 8.0)
    return np.clip(mapped_light[..., None] + chroma * compression[..., None], 0.0, 1.0)


def make_table(look):
    axis = np.linspace(0.0, 1.0, SIZE)
    b, g, r = np.meshgrid(axis, axis, axis, indexing="ij")
    rgb = np.stack([r, g, b], axis=-1)
    return grade(rgb, look).astype(np.float32)


def sample_table(table, rgb):
    # Same red-fastest trilinear layout as the app, with float32 persisted values.
    grid = table.shape[0]
    pos = np.clip(rgb, 0.0, 1.0) * (grid - 1)
    low = np.floor(pos).astype(np.int32)
    high = np.minimum(low + 1, grid - 1)
    weights = pos - low
    out = np.zeros_like(rgb, dtype=np.float64)
    for z in (0, 1):
        for y in (0, 1):
            for x in (0, 1):
                indices = [high[..., c] if corner else low[..., c] for c, corner in enumerate((x, y, z))]
                weight = np.prod(np.stack([weights[..., c] if corner else 1.0 - weights[..., c]
                                           for c, corner in enumerate((x, y, z))]), axis=0)
                out += table[indices[2], indices[1], indices[0]] * weight[..., None]
    return out


def study_image():
    # An original synthetic chart: no third-party photos or claimed camera captures.
    width, height = 384, 280
    rgb = np.zeros((height, width, 3), dtype=np.float64)
    t = np.linspace(0.0, 1.0, width)
    rgb[:70] = np.stack([.12 + .68 * t, .35 + .51 * t, .70 + .25 * t], axis=-1)
    skin = [[.85, .63, .50], [.65, .43, .32], [.42, .27, .19], [.95, .82, .73]]
    foliage = [[.18, .38, .20], [.36, .57, .30], [.73, .26, .17], [.94, .69, .20]]
    for i in range(4):
        rgb[70:140, i * 96:(i + 1) * 96] = skin[i]
        rgb[140:210, i * 96:(i + 1) * 96] = foliage[i]
    rgb[210:] = np.repeat(t[..., None], 3, axis=-1)
    return rgb


def font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


def preview(tables, output):
    source = study_image()
    sheet = Image.new("RGB", (1680, 880), "#121310")
    draw = ImageDraw.Draw(sheet)
    draw.text((36, 28), "GRAIN ORIGINALS", font=font(34), fill="#ead7a3")
    draw.text((36, 76), "Sky / warm skin swatches / foliage + color / neutral ramp", font=font(19), fill="#cac9c3")
    frames = [("Original", source)] + [(look.title, sample_table(tables[look.slug], source)) for look in LOOKS]
    for i, (title, colors) in enumerate(frames):
        x, y = 36 + (i % 4) * 410, 132 + (i // 4) * 346
        image = Image.fromarray(np.round(np.clip(colors, 0.0, 1.0) * 255).astype(np.uint8))
        sheet.paste(image, (x, y))
        draw.text((x, y + 292), title, font=font(22), fill="#f3f1eb")
    draw.text((36, 826), "100% strength / sRGB / synthetic color study / real photos and device rendering pending", font=font(17), fill="#aeadA5")
    sheet.save(output)


def export(root, check=False):
    assets = root / "app/src/main/assets/luts/grain"
    exports = root / "output/luts/grain-originals-v044"
    design = root / "output/design"
    tables = {look.slug: make_table(look) for look in LOOKS}
    if not check:
        for directory in (assets, exports, design):
            directory.mkdir(parents=True, exist_ok=True)
    manifest = []
    for look in LOOKS:
        table = tables[look.slug]
        blob = b"GRAINLUT" + struct.pack(">II", 1, SIZE) + table.astype(">f4").tobytes()
        path = assets / f"{look.slug}.glut"
        if check:
            if not path.exists() or path.read_bytes() != blob:
                raise SystemExit(f"Stale or missing original LUT: {path}")
        else:
            path.write_bytes(blob)
            with (exports / f"Grain-{look.title.replace(' ', '-')}-sRGB-33.cube").open("w", encoding="utf-8") as cube:
                cube.write(f'# Grain Originals v1 | Original creative look; not a measured film-stock reproduction.\n'
                           f'# Input: sRGB SDR | Output: sRGB SDR | Strength: 100%\n'
                           f'TITLE "{look.title}"\nLUT_3D_SIZE {SIZE}\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n')
                for pixel in table.reshape(-1, 3):
                    cube.write(" ".join(format(float(channel), ".9g") for channel in pixel) + "\n")
        manifest.append({"id": f"grain-{look.slug}", "title": look.title,
                         "input": "sRGB SDR", "output": "sRGB SDR", "grid": SIZE,
                         "bytes": len(blob), "sha256": hashlib.sha256(blob).hexdigest()})
    if not check:
        (exports / "README.txt").write_text(
            "Grain Originals v1\nSix original creative looks for sRGB SDR input and output.\n"
            "These are inspired by film aesthetics, not calibrated reproductions of named film stocks.\n"
            "No grain, halation, HDR or Log conversion is included. Start at 100% and adjust to taste.\n"
            "CUBE layout: red changes fastest. Float32 tables match the Android app.\n"
            "Generated from scripts/generate_grain_originals.py in the Grain project.\n", encoding="utf-8")
        (assets / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        preview(tables, design / "grain-originals-v044-preview.png")
        with zipfile.ZipFile(root / "output/Grain-Originals-v1-sRGB-LUTs.zip", "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(exports.iterdir()):
                archive.write(path, path.name)
    print(json.dumps({"looks": len(tables), "asset_bytes": sum(entry["bytes"] for entry in manifest),
                      "check": check}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--check", action="store_true", help="Check baked assets against the authored transforms")
    args = parser.parse_args()
    export(args.root, args.check)
