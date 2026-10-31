#!/usr/bin/env bash
# Builds the frontend, every bundled core and the APKs.
#
# Usage: scripts/build-apk.sh [assembleDebug|assembleRelease] [core-id ...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK="${1:-assembleDebug}"
if [[ $# -gt 0 ]]; then shift; fi
JNI_LIBS="$ROOT/android/app/src/main/jniLibs"

echo "==> frontend"
mkdir -p "$JNI_LIBS"
(
  cd "$ROOT"
  cargo ndk -t arm64-v8a -t x86_64 -o "$JNI_LIBS" build --release -p retro-frontend
)

echo "==> cores"
"$ROOT/scripts/build-cores.sh" "$@"

echo "==> apk"
cd "$ROOT/android"
./gradlew "$TASK" --no-daemon

echo
echo "APK output:"
find "$ROOT/android/app/build/outputs/apk" -name "*.apk" -newermt "-10 minutes" -print
