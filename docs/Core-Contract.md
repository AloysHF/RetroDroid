# RetroDroid Core Contract

RetroDroid is a libretro frontend for Android. It does not emulate anything
itself: every system is a libretro core (`*.so`) compiled from one of the
emulator repositories in this family and shipped inside the APK.

This document is the contract between RetroDroid and those cores. A core that
satisfies it works in RetroDroid, RetroArch and any other conforming frontend
without per-app patches.

## 1. Core requirements

### 1.1 Content and paths

- A core that loads game content must report `need_fullpath = true`. RetroDroid
  hands the core a real file path, never a memory buffer.
- A firmware-only system (no game media, for example WQX) sets
  `need_fullpath = false` and `no_game = true` in its profile. RetroDroid calls
  `retro_load_game(NULL)` and the core boots its firmware from the system
  directory. Such a core must not reject a null `retro_game_info`.
- `valid_extensions` must be a `|`-separated list of lowercase extensions
  without dots. RetroDroid uses it to decide which system a game belongs to.
  A firmware-only system leaves it empty.
- A game may consist of a directory tree (for example a package with a menu
  file and several data files). RetroDroid copies the user's selection out of
  SAF into an app-private cache directory before loading, so the core may use
  relative paths, `std::fs::read_dir` and any other filesystem API freely.
  When the selection is a `.cue` sheet, the tracks it names are copied beside
  it; no other file from the folder is copied.
- Archives (`.zip`) are passed to the core untouched only when the core lists
  `zip` in `valid_extensions` and can boot a package itself. Otherwise
  RetroDroid extracts the archive into the cache directory first.

### 1.2 Directories

- Game saves (battery-backed save files) must go to the path returned by
  `RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY`. RetroDroid returns a persistent
  directory per system. Never write saves next to the content: that location
  can be a temporary cache copy that is deleted on exit.
- Firmware/BIOS files must be looked up in the path returned by
  `RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY`. RetroDroid returns one shared,
  persistent system root for every core. A core must therefore use its own
  fixed subfolder and exact file names: `bbkemu/a4980/8.BIN`,
  `wqxemu/<model>/<model>.rom`, `hyperscanemu/spg290.bin`. Names are
  case-sensitive; there is no search across sibling folders and no
  content-based discovery.
- The same relative layout is used by the standalone emulator, RetroArch,
  EmulatorJS and RetroDroid, so one firmware tree serves every frontend.
- Any other user-visible asset (keyboard art, fonts) also belongs in the system
  directory; it must never be read from the APK assets or the current working
  directory.

### 1.3 Video

- The core must call `RETRO_ENVIRONMENT_SET_PIXEL_FORMAT` during
  `retro_load_game()` (or earlier) and must emit frames in exactly the declared
  format. RetroDroid supports `0RGB1555`, `RGB565` and `XRGB8888`.
- `retro_get_system_av_info()` must return the real base resolution and sample
  rate. RetroDroid uses the geometry for aspect-ratio layout; a core that
  changes resolution at runtime must also call `RETRO_ENVIRONMENT_SET_GEOMETRY`.
- Frame pacing is driven by the frontend at the reported `fps`.

### 1.4 Audio

- Audio must be produced through `retro_audio_sample_batch` with signed 16-bit
  stereo frames, `sample_rate` as reported by `retro_get_system_av_info()`.
- A core may emit silence when muted; it must not block or allocate unbounded
  buffers when the frontend consumes audio more slowly than it is produced.

### 1.5 Errors

- Every failure reachable from user input (missing firmware, unsupported
  content, corrupt save) must be reported both to the log interface
  (`RETRO_ENVIRONMENT_GET_LOG_INTERFACE`) and to the user through
  `RETRO_ENVIRONMENT_SET_MESSAGE` before returning `false` from
  `retro_load_game()`. A silent `false` is a contract violation.
- The log callback is the standard printf-style
  `void (*)(enum retro_log_level, const char *fmt, ...)`. RetroDroid formats
  the arguments before displaying the message, so `log_cb(level, "%s", text)`
  and a plain format string both work.
- No entry point may unwind across the C ABI. Wrap fallible bodies in
  `std::panic::catch_unwind` and return a neutral value (`false`, `0`, silence)
  on panic.

### 1.6 Save states

- `retro_serialize_size()` must be stable for a given content while it is
  loaded. The frontend reads the size once and reuses it.
- Serialized states must start with a 4-byte magic and a little-endian `u32`
  format version. `retro_unserialize()` must reject unknown versions with a log
  warning and `false` instead of interpreting foreign bytes as machine state.
  Recommended header: `b"<CORE>"` + version, e.g. `b"N32S"` + `1u32`.
- A state written by one build must either load on a later build of the same
  format version or be rejected; never crash.

### 1.7 Core options

- Declare options with `RETRO_ENVIRONMENT_SET_VARIABLES` (legacy interface).
  RetroDroid does not implement `RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION`.
  Each value string is `"Label; choice1|choice2|..."`, first choice = default.
- Read the selected value with `RETRO_ENVIRONMENT_GET_VARIABLE` at load time
  and again whenever `RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE` reports `true`.
- Options must be namespaced with the core name (`native32emu_volume`), so
  multiple systems can share one settings store.

### 1.8 Input

- Poll the joypad with `RETRO_DEVICE_JOYPAD`, port 0. The frontend maps the
  on-screen pad and physical controllers to standard retro IDs and sets them as
  a bitmask; cores must not require analog, rumble or multitap devices.
- Keyboard-driven systems (for example WQX) must implement
  `RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK` and consume
  `RETRO_DEVICE_KEYBOARD` state from `retro_input_state`.
- Input must be edge-stable: pressing a key for N frames is seen as N frames.
  Cores should use the provided input snapshot once per `retro_run()`.

## 2. Android build requirements

- Build with the NDK and **16 KB page alignment**, required by Android 15+:

  ```bash
  export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
  cargo ndk -t arm64-v8a -t x86_64 --platform 21 build -p <crate> --release
  ```

- Produce a `cdylib` whose file name matches `so_name` in the profile below.
- Do not link against libraries that are not part of the NDK or the core
  itself. `libc++_shared.so` is acceptable only if shipped next to the core.

## 3. `android.json` profile

Every core ships one `android.json` at
`crates/<core>-libretro/android.json`. RetroDroid consumes it at build time to
generate the system registry; nothing else is hard-coded in the app.

```json
{
  "schema": 1,
  "core": "native32emu",
  "so_name": "libnative32emu.so",
  "system": "native32",
  "display_name": { "en": "Native32", "zh": "凌阳 Native32" },
  "short_name": "Native32",
  "manufacturer": "Sunplus",
  "supported_extensions": ["smf", "sgm", "ssl", "zip"],
  "no_game": false,
  "boot_options": [],
  "save_state_format": 1,
  "firmware": [],
  "input": { "preset": "dpad-ab-select" },
  "video": { "orientation": "landscape", "pixel_format": "xrgb8888" },
  "notes": {
    "en": "Sunplus SPG series educational handhelds.",
    "zh": "凌阳 SPG 系列教育掌机。"
  }
}
```

| Field | Meaning |
| --- | --- |
| `core` | Stable id, used for directories and option storage. |
| `so_name` | File name installed into `jniLibs/<abi>/`. |
| `system` | System id shown in the app. Usually equals `core`. |
| `display_name` | Bilingual names. |
| `supported_extensions` | Superset of the core's `valid_extensions`; used for ROM discovery only. Empty for firmware-only systems. |
| `no_game` | `true` when the core boots without content (`retro_load_game(NULL)`); RetroDroid then shows a model selector and a **Start firmware** button instead of a game list. |
| `boot_options` | Model choices shown before boot: `[{ "key": "wqxemu_model", "label": { "en": "...", "zh": "..." }, "values": ["nc1020", "..."] }]`. The first value is the default; the selection is stored as the core option `key`. |
| `save_state_format` | Version written into save-state envelopes; must match the core. |
| `firmware` | Files the core reads from the system directory, relative to the shared system root: `[{ "file": "wqxemu/nc1020/nc1020.rom", "required": true, "hint": { "en": "...", "zh": "..." } }]`. RetroDroid shows the same path below `BIOS/<system>/` with the leading core folder hidden. `required: false` marks an optional support file (for example `coolbaremu/coolbaremu_orientation.csv`): it is copied when present and never blocks loading. |
| `input.preset` | Frontend layout id: `dpad-ab-select`, `dpad-abcd-select`, `dpad-ab-lr-select`, `qwerty`. |
| `video.orientation` | Preferred emulation orientation: `landscape` or `portrait`. |

## 4. Reference implementation

`Native32Emu` (`crates/native32emu-libretro`) is the reference: save directory
plumbing, `SET_MESSAGE` error reporting, panic isolation, versioned save states
and the 16 KB build flags are all present there. New cores should copy that
shape rather than invent another one.
