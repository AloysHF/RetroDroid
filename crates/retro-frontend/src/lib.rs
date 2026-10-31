//! RetroDroid's libretro frontend runtime.
//!
//! This crate is compiled into `libretro_frontend.so` and driven from Kotlin
//! through [`jni_api`]. It implements the libretro contract documented in
//! `docs/Core-Contract.md`: software video, audio, input, save states and core
//! options. Cores are loaded from the APK's native library directory, so no
//! part of the core lifecycle involves the network.

// The JNI surface is the only consumer of most of this crate; host builds only
// run the unit tests of the pure modules.
#![cfg_attr(not(target_os = "android"), allow(dead_code))]

mod audio;
mod blit;
mod env;
mod ffi;
mod input;
mod keymap;
mod options;
mod session;
mod video;
mod window;

#[cfg(target_os = "android")]
mod jni_api;

pub use session::{av_info_json, is_loaded, is_running, system_info_json};
