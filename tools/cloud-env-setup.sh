#!/usr/bin/env bash
# Claude environment Setup script: bash tools/cloud-env-setup.sh
# Requires Custom network access with the default domains plus dl.google.com.
set -euo pipefail
if [ "$(uname -s)" != Linux ]; then
  echo 'This provisioning script is for the Claude Linux cloud VM.' >&2
  exit 1
fi
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
if [ ! -x "$JAVA_HOME/bin/java" ] || ! command -v unzip >/dev/null || ! command -v curl >/dev/null; then
  apt-get update -qq
  apt-get install -y -qq openjdk-17-jdk-headless unzip curl python3
fi
grain_sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$grain_sdkmanager" ]; then
  grain_temp=$(mktemp -d)
  trap 'rm -rf "$grain_temp"' EXIT
  curl --fail --location --retry 3 --max-time 180 --silent --show-error \
    https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o "$grain_temp/tools.zip"
  printf '%s  %s\n' '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583' "$grain_temp/tools.zip" | sha256sum -c -
  unzip -q "$grain_temp/tools.zip" -d "$grain_temp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$grain_temp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# yes can exit with SIGPIPE; only sdkmanager's exit status determines success.
set +o pipefail
yes | "$grain_sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
grain_licenses_status=${PIPESTATUS[1]}
set -o pipefail
[ "$grain_licenses_status" -eq 0 ]
"$grain_sdkmanager" --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-37' 'build-tools;36.0.0'
echo 'Grain cloud toolchain ready: JDK 17, Android 37, Build Tools 36.0.0.'
