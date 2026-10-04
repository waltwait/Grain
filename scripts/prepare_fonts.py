#!/usr/bin/env python3
"""Subset the SIL OFL fonts Grain bundles (Noto Sans TC for the UI, Newsreader for the brand title).

Local files only; nothing is downloaded. Needs fontTools and brotli (pip install fonttools brotli).

  python3 scripts/prepare_fonts.py --source-dir DIR --revision GOOGLE_FONTS_COMMIT

DIR holds the unmodified google/fonts files at that commit:
  NotoSansTC[wght].ttf, Newsreader[opsz,wght].ttf, OFL-NotoSansTC.txt, OFL-Newsreader.txt
"""
import argparse
import shutil
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

from verify_fonts import ASSET_DIR, BRAND, FONT_DIR, SANS, big5_level1, ui_characters

LATIN = "".join(chr(c) for c in [*range(0x20, 0x7F), *range(0xA0, 0x100)])
PUNCTUATION = "，。、；：？！「」『』（）《》〈〉—…·～＋－＝％＃＆＠／＼｜＿★☆○●◎□■△▲▽▼→←↑↓※　"
ZHUYIN = "".join(chr(c) for c in range(0x3105, 0x312A)) + "ˇˊˋ˙"
FEATURES = ["kern", "liga", "ccmp", "locl", "mark", "mkmk"]


def build(source: Path, target: Path, text: str, limits: dict) -> None:
    font = TTFont(source)
    options = subset.Options()
    options.layout_features = FEATURES
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]  # keep the copyright and license notices inside the font
    options.hinting = False
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    # Limit the axes after subsetting: partial instancing first leaves gvar without entries for some glyphs,
    # which the subsetter then fails on (KeyError).
    font = instancer.instantiateVariableFont(font, limits)
    target.parent.mkdir(parents=True, exist_ok=True)
    font.save(target)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--revision", required=True, help="google/fonts commit the source files came from")
    args = parser.parse_args()
    sans_text = LATIN + PUNCTUATION + ZHUYIN + "".join(sorted(big5_level1() | ui_characters()))
    build(args.source_dir / "NotoSansTC[wght].ttf", SANS, sans_text, {"wght": (400, 700)})
    build(args.source_dir / "Newsreader[opsz,wght].ttf", BRAND, LATIN + PUNCTUATION, {"opsz": 24, "wght": (400, 700)})
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    notices = {}
    for name in ("NotoSansTC", "Newsreader"):
        license_file = args.source_dir / f"OFL-{name}.txt"
        shutil.copyfile(license_file, ASSET_DIR / license_file.name)
        notices[name] = license_file.read_text(encoding="utf-8").splitlines()[0]
    (ASSET_DIR / "ATTRIBUTION.txt").write_text(f"""Fonts bundled with Grain

Noto Sans TC (interface text)
{notices['NotoSansTC']}
Source: https://github.com/google/fonts/tree/{args.revision}/ofl/notosanstc

Newsreader (the "Grain" brand title)
{notices['Newsreader']}
Source: https://github.com/google/fonts/tree/{args.revision}/ofl/newsreader

License: SIL Open Font License, Version 1.1 (full texts: OFL-NotoSansTC.txt, OFL-Newsreader.txt)
https://openfontlicense.org
Retain these notices when sharing the fonts.

Modifications by the Grain project, 2026-10-04 (scripts/prepare_fonts.py):
Both fonts are subsets. Noto Sans TC keeps Latin, punctuation, Zhuyin, the Big5 level-1 common
Traditional Chinese characters and every character used by the app's own text; the weight axis is
limited to 400-700. Newsreader keeps Latin and punctuation, with the optical size fixed at 24 and
the weight axis limited to 400-700. TrueType hinting was removed. Characters outside a subset fall
back to the system font. No glyph outlines were redrawn.
""", encoding="utf-8")
    print(f"Wrote {SANS.name} ({SANS.stat().st_size:,} bytes) and {BRAND.name} ({BRAND.stat().st_size:,} bytes) to {FONT_DIR}")


if __name__ == "__main__":
    main()
