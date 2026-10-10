#!/usr/bin/env bash
# Use the same JDK and bounded workers locally, in Claude cloud, and in Actions.
set -euo pipefail
grain_root=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then
  for grain_jdk in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /usr/lib/jvm/java-17-openjdk-amd64; do
    if [ -x "$grain_jdk/bin/java" ]; then export JAVA_HOME="$grain_jdk"; break; fi
  done
fi
grain_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$grain_java" -version 2>&1 | head -n 1 | grep -Eq 'version "17[.\"]'; then
  echo 'Grain requires JDK 17. Set JAVA_HOME before building.' >&2
  exit 1
fi
cd "$grain_root"
grain_java_home=${JAVA_HOME:-$("$grain_java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p')}
exec ./gradlew -Dorg.gradle.java.home="$grain_java_home" --max-workers="${GRAIN_MAX_WORKERS:-2}" "$@"
