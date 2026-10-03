#!/usr/bin/env python3
"""Create Grain's first local production key without logging passwords or replacing keys."""

import os
from pathlib import Path
import secrets
import shutil
import subprocess


def main():
    project = Path(__file__).resolve().parents[1]
    directory = project / ".signing"
    directory.mkdir(mode=0o700, exist_ok=True)
    directory.chmod(0o700)
    key = directory / "grain-release.jks"
    settings = directory / "release.properties"
    if key.exists() or settings.exists():
        raise SystemExit("Signing files already exist; keep the existing key and configuration.")

    java_home = os.environ.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else shutil.which("keytool")
    if not keytool:
        raise SystemExit("Set JAVA_HOME to JDK 17 before creating a release key.")

    password = secrets.token_urlsafe(48)
    environment = dict(os.environ, GRAIN_KEY_PASSWORD=password)
    # Store settings first so a keytool failure never leaves an unrecoverable key.
    with open(settings, "x", opener=lambda name, flags: os.open(name, flags, 0o600)) as output:
        output.write("storeFile=.signing/grain-release.jks\nstoreType=PKCS12\n")
        output.write(f"storePassword={password}\nkeyAlias=grain-release\nkeyPassword={password}\n")
    settings.chmod(0o600)
    result = subprocess.run([
        keytool, "-genkeypair", "-noprompt", "-keystore", str(key), "-storetype", "PKCS12",
        "-alias", "grain-release", "-keyalg", "RSA", "-keysize", "4096", "-sigalg", "SHA256withRSA",
        "-validity", "10000", "-dname", "CN=Grain", "-storepass:env", "GRAIN_KEY_PASSWORD",
        "-keypass:env", "GRAIN_KEY_PASSWORD",
    ], env=environment, check=False, capture_output=True, text=True)
    if result.returncode:
        # Do not print subprocess output: signing tools must never echo credentials into logs.
        if not key.exists():
            settings.unlink()
        raise SystemExit("Key creation failed. Any partial signing files were retained for local inspection.")
    key.chmod(0o600)
    print("Created the local Grain release key. Back up .signing securely; never upload it to GitHub.")


if __name__ == "__main__":
    main()
