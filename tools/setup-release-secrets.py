#!/usr/bin/env python3
"""Verify the local Grain signer; --upload provisions main-only Actions secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from prepare_update_site import PRODUCTION_CERTIFICATE

REPO = "waltwait/Grain"


def gh(args, data=None):
    return subprocess.run(["gh", *args], input=data, capture_output=True, check=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upload", action="store_true", help="Upload the existing signer to GitHub's main-only release environment")
    args = parser.parse_args()
    # The existing generated properties contain plain ASCII values. Reject escaped values rather than misreading a password.
    values = {}
    for line in (ROOT / ".signing/release.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            if "\\" in value:
                raise ValueError("Escaped signing properties need manual configuration")
            values[key.strip()] = value.strip()
    for key in ("storeFile", "storePassword", "keyAlias", "keyPassword", "storeType"):
        if not values.get(key):
            raise ValueError("Missing local signing setting")
    keyfile = ROOT / values["storeFile"]
    keytool = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")) / "bin/keytool"
    certificate = subprocess.run([str(keytool), "-exportcert", "-keystore", str(keyfile),
                                  "-storetype", values["storeType"], "-alias", values["keyAlias"],
                                  "-storepass:env", "GRAIN_LOCAL_VERIFY_PASSWORD"],
                                 env=dict(os.environ, GRAIN_LOCAL_VERIFY_PASSWORD=values["storePassword"]),
                                 check=True, capture_output=True).stdout
    if hashlib.sha256(certificate).hexdigest() != PRODUCTION_CERTIFICATE:
        raise ValueError("Local key does not match the installed production signer")
    print("Existing production certificate verified. No secret values are printed.")
    if not args.upload:
        print("Check only: no signing material uploaded.")
        return
    repository = json.loads(gh(["api", "repos/" + REPO]))
    if repository["full_name"] != REPO or not repository["permissions"]["admin"]:
        raise ValueError("Administrative access to the exact Grain repository is required")
    environments = json.loads(gh(["api", f"repos/{REPO}/environments"]))["environments"]
    if not any(e["name"] == "release" for e in environments):
        policy = json.dumps({"deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True}}).encode()
        gh(["api", "--method", "PUT", f"repos/{REPO}/environments/release", "--input", "-"], policy)
        gh(["api", "--method", "POST", f"repos/{REPO}/environments/release/deployment-branch-policies", "--input", "-"],
           json.dumps({"name": "main", "type": "branch"}).encode())
    environment = json.loads(gh(["api", f"repos/{REPO}/environments/release"]))
    policy = environment["deployment_branch_policy"]
    branches = json.loads(gh(["api", f"repos/{REPO}/environments/release/deployment-branch-policies"]))["branch_policies"]
    if not policy or not policy["custom_branch_policies"] or policy["protected_branches"] or \
            len(branches) != 1 or branches[0]["name"] != "main" or branches[0]["type"] != "branch":
        raise ValueError("Release environment must permit only the main branch; no repository-secret fallback")
    secrets = {
        "GRAIN_KEYSTORE_BASE64": base64.b64encode(keyfile.read_bytes()),
        "GRAIN_RELEASE_STORE_PASSWORD": values["storePassword"].encode(),
        "GRAIN_RELEASE_KEY_ALIAS": values["keyAlias"].encode(),
        "GRAIN_RELEASE_KEY_PASSWORD": values["keyPassword"].encode(),
        "GRAIN_RELEASE_STORE_TYPE": values["storeType"].encode(),
    }
    if any(len(value) >= 48 * 1024 for value in secrets.values()):
        raise ValueError("Signing secret exceeds GitHub's secret size limit")
    for name, value in secrets.items():
        gh(["secret", "set", name, "--repo", REPO, "--env", "release"], value)
        print("Configured release environment secret: " + name)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError):
        raise SystemExit("Signing setup failed; no secret values were logged. Inspect the signer and release environment locally.")
