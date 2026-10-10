#!/usr/bin/env python3
"""Restore an Actions secret to a temporary signing file, without logging it."""
import base64
import os
from pathlib import Path


def main() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise ValueError("This script only restores the Actions release signing secret")
    for name in ("GRAIN_KEYSTORE_BASE64", "GRAIN_RELEASE_STORE_PASSWORD", "GRAIN_RELEASE_KEY_ALIAS", "GRAIN_RELEASE_KEY_PASSWORD", "GRAIN_RELEASE_STORE_TYPE"):
        if not os.environ.get(name):
            raise ValueError("Missing release environment secret: " + name)
    data = base64.b64decode(os.environ["GRAIN_KEYSTORE_BASE64"], validate=True)
    target = Path(os.environ["RUNNER_TEMP"]) / "grain-signing/release.keystore"
    target.parent.mkdir(mode=0o700, exist_ok=True)
    with open(target, "xb", opener=lambda name, flags: os.open(name, flags, 0o600)) as output:
        output.write(data)
    with open(os.environ["GITHUB_ENV"], "a") as environment:
        environment.write(f"GRAIN_RELEASE_STORE_FILE={target}\n")
    print("Temporary release signing file restored; secret values are not logged.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError):
        raise SystemExit("Release signing restoration failed; check the release environment secrets.")
