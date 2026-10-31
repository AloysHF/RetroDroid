#!/usr/bin/env bash
# Builds every libretro core for Android and aggregates their profiles.
#
# Sources are taken from $RETRODROID_<ID>_SOURCE when set (useful while the
# adaptation PRs are unmerged), otherwise they are cloned from GitHub into
# build/sources/<id>.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_LIBS="$ROOT/android/app/src/main/jniLibs"
PROFILES="$ROOT/build/cores"
SOURCES="$ROOT/build/sources"

CORE_IDS=(native32 coolbar dingoo hyperscan lavax playdia spmp8000 wqx bbk)
CORE_REPOS=(
  "AloysHF/Native32Emu"
  "AloysHF/CoolbarEmu"
  "AloysHF/DingooEmu"
  "AloysHF/HyperScanEmu"
  "AloysHF/LavaXEmu"
  "AloysHF/PlaydiaEmu"
  "AloysHF/SPMP8000Emu"
  "AloysHF/WQXEmu"
  "AloysHF/BBKEmu"
)

if [[ -f "$ROOT/cores/local.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/cores/local.env"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$ANDROID_SDK" && -f "$ROOT/android/local.properties" ]]; then
    ANDROID_SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/android/local.properties" | tr -d '\r')"
  fi
  if [[ -n "$ANDROID_SDK" && -d "$ANDROID_SDK/ndk" ]]; then
    ANDROID_NDK_HOME="$ANDROID_SDK/ndk/$(ls "$ANDROID_SDK/ndk" | sort -V | tail -1)"
  fi
fi
export ANDROID_NDK_HOME

if [[ -z "${ANDROID_NDK_HOME:-}" || ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME is not set and no NDK was found in the Android SDK." >&2
  exit 1
fi

mkdir -p "$JNI_LIBS" "$PROFILES" "$SOURCES"

selected=("$@")

resolve_source() {
  local id="$1" repo="$2"
  local var="RETRODROID_$(echo "$id" | tr '[:lower:]' '[:upper:]')_SOURCE"
  local override="${!var:-}"
  if [[ -n "$override" ]]; then
    if [[ ! -d "$override" ]]; then
      echo "  $var points to a missing directory: $override" >&2
      return 1
    fi
    echo "$override"
    return 0
  fi

  local dest="$SOURCES/$id"
  if [[ ! -d "$dest/.git" ]]; then
    echo "  cloning https://github.com/$repo.git" >&2
    git clone --depth 1 "https://github.com/$repo.git" "$dest" >&2 || return 1
  fi
  echo "$dest"
}

package_of() {
  local manifest="$1/Cargo.toml"
  awk '
    /^\[package\]/ { in_package = 1; next }
    /^\[/ { in_package = 0 }
    in_package && /^name[[:space:]]*=/ {
      sub(/^name[[:space:]]*=[[:space:]]*/, "")
      gsub(/"/, "")
      gsub(/[[:space:]]+$/, "")
      print
      exit
    }
  ' "$manifest"
}

build_core() {
  local id="$1" repo="$2"
  local src profile crate_dir package
  src="$(resolve_source "$id" "$repo")"

  profile="$(find "$src/crates" -maxdepth 2 -name android.json -print -quit 2>/dev/null || true)"
  if [[ -z "$profile" ]]; then
    echo "  no crates/*/android.json found in $src" >&2
    return 1
  fi
  crate_dir="$(dirname "$profile")"
  package="$(package_of "$crate_dir")"
  if [[ -z "$package" ]]; then
    echo "  cannot read the crate package name from $crate_dir/Cargo.toml" >&2
    return 1
  fi

  echo "==> $id ($package)"
  (
    cd "$src"
    RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384" \
      cargo ndk -t arm64-v8a -t x86_64 -o "$JNI_LIBS" build --release -p "$package"
  ) || return 1

  cp "$profile" "$PROFILES/$id.json"
}

built=()
for index in "${!CORE_IDS[@]}"; do
  id="${CORE_IDS[$index]}"
  if [[ ${#selected[@]} -gt 0 ]]; then
    match=0
    for want in "${selected[@]}"; do
      [[ "$want" == "$id" ]] && match=1
    done
    [[ "$match" == 1 ]] || continue
  fi
  if ! build_core "$id" "${CORE_REPOS[$index]}"; then
    if [[ "${RETRODROID_SKIP_MISSING:-0}" == "1" ]]; then
      echo "  skipping $id: no Android profile available yet" >&2
      continue
    fi
    exit 1
  fi
  built+=("$id")
done

if [[ ${#built[@]} -eq 0 ]]; then
  echo "No cores matched: ${selected[*]}" >&2
  exit 1
fi

node "$ROOT/scripts/gen-registry.mjs"
