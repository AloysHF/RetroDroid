# RetroDroid

An all-in-one libretro frontend for Android. One APK, nine systems, no
in-app core downloads: every emulator core ships inside the app and is updated
together with the app.

RetroDroid is the Android companion of the emulator family developed under
[AloysHF](https://github.com/AloysHF):

| System | Core | License |
| --- | --- | --- |
| Native32 (Sunplus SPG) | `native32emu` | BSD-3-Clause |
| WooBooKong / SPMP8000 | `spmp8000` | BSD-3-Clause |
| Coolbar (MTK) | `coolbar` | BSD-3-Clause |
| Dingoo A320 | `dingoo` | BSD-3-Clause |
| HyperScan | `hyperscan` | BSD-3-Clause |
| Playdia | `playdia` | BSD-3-Clause |
| BBK electronic dictionary | `bbkemu` | GPL-3.0 |
| LavaX | `lavax` | GPL-3.0 |
| WQX (文曲星) | `wqx` | GPL-3.0 |

RetroDroid itself is BSD-3-Clause (see [LICENSE](LICENSE)). Bundled core
binaries keep their own license; their sources live in the corresponding
repositories.

## Status

Work in progress. The frontend, the Android UI and the core adaptions are
landed incrementally; see `git log` for the current state.

## How it works

```
┌──────────────────────────────────────────────┐
│ Kotlin / Jetpack Compose UI                  │
│  system list · ROM browser (SAF) · settings  │
├──────────────────────────────────────────────┤
│ JNI  (com.aloyshf.retrodroid.core.NativeCore)│
├──────────────────────────────────────────────┤
│ Rust frontend  (crates/retro-frontend)       │
│  dlopen core · env callbacks · video blit    │
│  audio ring buffer · input · save states     │
├──────────────────────────────────────────────┤
│ libretro cores (jniLibs/<abi>/*.so)          │
└──────────────────────────────────────────────┘
```

- Cores are loaded from the APK's native library directory at runtime; there is
  no network access for core management.
- ROMs and firmware are user-supplied. RetroDroid never bundles copyrighted
  content.
- Each core describes itself with an `android.json` profile; the app builds its
  system registry from those files, so adding a system is adding a core and a
  profile.

The interface every core must satisfy is documented in
[docs/Core-Contract.md](docs/Core-Contract.md).

## Using the app

1. On the home screen pick the folder that holds the ROMs of a system
   (`SAF` - Downloads, an SD card or a USB drive). The choice is remembered.
2. Systems that need firmware show a banner on their game list. Import the
   files from there; a picked file is matched against the names in the core
   profile, so files land in the folders the core looks in (some systems keep
   one folder per model).
3. Select a game to start it. The pause menu offers save states, core options,
   scaling and screen rotation; the back gesture returns to the game list.

For disc-based systems select the `.cue` sheet: the tracks it references are
copied next to it automatically, so a folder holding several discs only
transfers the one being played.

## Building

Requirements: Rust with the Android targets, the Android NDK (r27+), JDK 17+,
and the Android SDK.

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
export ANDROID_NDK_HOME=/path/to/android-ndk
export ANDROID_SDK_ROOT=/path/to/android-sdk

# Build the frontend, every core and the APKs.
./scripts/build-apk.sh                    # debug
./scripts/build-apk.sh assembleRelease    # release
```

The core sources are cloned from GitHub into `build/sources/`. While a core
adaptation is still unmerged, point the script at your local checkout instead:

```bash
cp cores/local.env.example cores/local.env
# RETRODROID_NATIVE32_SOURCE=/path/to/Native32Emu
```

Outputs land in `android/app/build/outputs/apk/`: per-ABI APKs
(`arm64-v8a`, `x86_64`) plus the universal APK. CI builds the same set on every
push and attaches the APKs to the release on `v*` tags; cores whose
`android.json` profile is not merged yet are skipped there
(`RETRODROID_SKIP_MISSING=1`) so the workflow stays green until the adaptation
PRs land.

## Legal

RetroDroid does not include games or console firmware. Users must own the
content they run. The three GPL-3.0 cores are bundled as separate dynamic
libraries and their complete sources are published in their own repositories.
