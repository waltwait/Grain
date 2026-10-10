#!/usr/bin/env bash
set -euo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = true ] || exit 0
grain_root=$(cd "$(dirname "$0")/.." && pwd)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
if [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -d "$ANDROID_HOME/platforms/android-37" ] || [ ! -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]; then
  bash "$grain_root/tools/cloud-env-setup.sh"
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  printf 'export JAVA_HOME=%q\nexport ANDROID_HOME=%q\n' "$JAVA_HOME" "$ANDROID_HOME" >> "$CLAUDE_ENV_FILE"
fi
python3 "$grain_root/scripts/restore_fuji_assets.py"
echo 'Grain ready. Use bash tools/gradle.sh :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest.'
