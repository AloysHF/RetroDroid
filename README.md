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
│  system list · fixed on-device library       │
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

RetroDroid reads games and firmware from one fixed folder on the device, so
there is nothing to pick on every launch:

```
/sdcard/RetroDroid/
├── ROMs/
│   ├── native32/
│   ├── coolbar/
│   ├── dingoo/
│   ├── hyperscan/
│   ├── lavax/
│   ├── playdia/
│   ├── spmp8000/
│   ├── wqx/
│   └── bbk/
└── BIOS/
    ├── bbk/
    │   └── A4980/          8.BIN, E.BIN
    ├── hyperscan/          spg290.bin, hyperscan.bin
    └── wqx/
        ├── nc1020/         obj_lu.bin, nc1020.fls
        ├── pc1000/         pc1000.rom, pc1000.nor
        ├── cc800/          obj.bin, cc800.fls
        ├── nc2000/         nc2000.nor, nc2000.nand, nc2000.nand0
        └── nc3000/         nc3000.nor, nc3000.nand, nc3000.nand0
```

1. On first launch, grant `All files access` so the app can read the library.
   The folder tree is created automatically; drop game files into the
   subfolder of their system.
2. Firmware lives in `BIOS/<system>/`, with one subfolder per model on systems
   whose core loads model-specific files (BBK A4980, the WQX models). The game
   list shows the exact paths a system still needs; matching files are copied
   into the core's private directory on refresh. Only systems that need
   firmware get a `BIOS/<system>/` folder.
3. Select a game to start it. The pause menu offers save states, core options,
   scaling and screen rotation; the back gesture returns to the game list.

Games are opened in place from the library. For disc-based systems keep the
`.cue` sheet next to its tracks (`ROMs/playdia/game.cue` and
`ROMs/playdia/game.bin`).

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

The core sources are cloned from GitHub into `build/sources/`. To build a core
from a local checkout instead:

```bash
cp cores/local.env.example cores/local.env
# RETRODROID_NATIVE32_SOURCE=/path/to/Native32Emu
```

Outputs land in `android/app/build/outputs/apk/`: per-ABI APKs
(`arm64-v8a`, `x86_64`) plus the universal APK. CI builds the same set on every
push and attaches the APKs to the release on `v*` tags.

## Legal

RetroDroid does not include games or console firmware. Users must own the
content they run. The three GPL-3.0 cores are bundled as separate dynamic
libraries and their complete sources are published in their own repositories.
