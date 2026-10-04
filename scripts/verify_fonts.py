#!/usr/bin/env python3
"""Verify the bundled Grain fonts: variable weights, glyph coverage for the real UI text, licenses, size budget.

Needs fontTools (pip install fonttools). Run from anywhere:  python3 scripts/verify_fonts.py
"""
import re
import sys
from pathlib import Path

from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "app/src/main/res/font"
ASSET_DIR = ROOT / "app/src/main/assets/fonts"
SANS = FONT_DIR / "noto_sans_tc.ttf"
BRAND = FONT_DIR / "newsreader.ttf"
SANS_BUDGET = 3_500_000
BRAND_BUDGET = 400_000
CJK = re.compile(r"[　-〿ㄅ-ㄯ㐀-鿿＀-￯]")


def ui_characters() -> set[str]:
    """Every CJK character the app can show: Kotlin sources and XML resources."""
    chars: set[str] = set()
    for path in [*ROOT.joinpath("app/src/main/java").rglob("*.kt"), *ROOT.joinpath("app/src/main/res").rglob("*.xml")]:
        chars.update(CJK.findall(path.read_text(encoding="utf-8")))
    return chars


def big5_level1() -> set[str]:
    chars = set()
    for lead in range(0xA4, 0xC7):
        for trail in [*range(0x40, 0x7F), *range(0xA1, 0xFF)]:
            try:
                text = bytes([lead, trail]).decode("big5")
            except UnicodeDecodeError:
                continue
            if len(text) == 1 and "一" <= text <= "鿿":
                chars.add(text)
    return chars


def check(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def weight_axis(font: TTFont):
    return next((a for a in font["fvar"].axes if a.axisTag == "wght"), None) if "fvar" in font else None


def main() -> int:
    errors: list[str] = []
    for path, budget in ((SANS, SANS_BUDGET), (BRAND, BRAND_BUDGET)):
        if not path.is_file():
            errors.append(f"missing {path.relative_to(ROOT)}")
            continue
        check(path.stat().st_size <= budget, f"{path.name} is {path.stat().st_size} bytes, budget {budget}", errors)
        font = TTFont(path)
        axis = weight_axis(font)
        check(axis is not None and axis.minValue <= 400 and axis.maxValue >= 700, f"{path.name} must keep a wght axis covering 400-700", errors)
        cmap = font.getBestCmap()
        check(all(ord(c) in cmap for c in map(chr, range(0x20, 0x7F))), f"{path.name} is missing printable ASCII", errors)
        if path == SANS:
            ui = ui_characters()
            missing_ui = sorted(c for c in ui if ord(c) not in cmap)
            check(not missing_ui, f"{path.name} lacks UI characters: {''.join(missing_ui)}", errors)
            missing_common = [c for c in big5_level1() if ord(c) not in cmap]
            check(not missing_common, f"{path.name} lacks {len(missing_common)} common Big5 level-1 characters", errors)
            print(f"{path.name}: {path.stat().st_size:,} bytes, {len(ui)} UI characters, Big5 level 1 covered")
        else:
            check(all(ord(c) in cmap for c in "Grain"), f"{path.name} must cover the brand title", errors)
            print(f"{path.name}: {path.stat().st_size:,} bytes")
    for name, needle in (("ATTRIBUTION.txt", "SIL Open Font License"), ("OFL-NotoSansTC.txt", "SIL OPEN FONT LICENSE Version 1.1"), ("OFL-Newsreader.txt", "SIL OPEN FONT LICENSE Version 1.1")):
        target = ASSET_DIR / name
        check(target.is_file() and needle.lower() in target.read_text(encoding="utf-8").lower(), f"assets/fonts/{name} missing or incomplete", errors)
    for message in errors:
        print("FAIL:", message)
    if not errors:
        print("Fonts verified.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
