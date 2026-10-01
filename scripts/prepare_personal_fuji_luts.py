#!/usr/bin/env python3
"""Extract unchanged official files into local debug assets for personal testing."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("zip", type=Path, help="Official GFX ETERNA 55 Ver.1.10 ZIP")
args = parser.parse_args()
expected = "febfc7050999620651ca0cf162bf8b499970ef270b4da632765b61b958cf7940"
digest = hashlib.sha256(args.zip.read_bytes()).hexdigest()
if digest != expected:
    raise SystemExit("ZIP checksum differs from the reviewed official Ver.1.10 package.")

styles = ["CLASSIC-CHROME", "CLASSIC-Neg.", "REALA-ACE", "PROVIA", "Velvia",
          "ASTIA", "PRO-Neg.Std", "ETERNA", "ETERNA-BB", "ACROS"]
destination = Path(__file__).resolve().parents[1] / "app/src/debug/assets/luts/fujifilm"
destination.mkdir(parents=True, exist_ok=True)
manifest = {
    "source": "https://www.fujifilm-x.com/global/support/download/lut/",
    "package": "GFX ETERNA 55 Ver.1.10",
    "package_sha256": digest,
    "copyright": "FUJIFILM Corporation",
    "usage": "Local personal debug build; public redistribution rights not established.",
    "files": [],
}
with ZipFile(args.zip) as archive:
    for style in styles:
        filename = f"FLog2_to_{style}_33grid_V.1.00.cube"
        member = f"gfx-eterna-55-3d-lut-v110/33Grid/F-Log2/{filename}"
        data = archive.read(member)
        (destination / filename).write_bytes(data)
        manifest["files"].append({"file": filename, "member": member,
                                  "sha256": hashlib.sha256(data).hexdigest()})
(destination / "sources.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(f"Prepared {len(styles)} unchanged F-Log2 33-grid LUTs in {destination}")
