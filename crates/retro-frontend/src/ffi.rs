//! libretro C ABI declarations used by the frontend.
//!
//! These mirror the public `libretro.h` interface, reduced to what RetroDroid
//! implements. They are deliberately plain data so the module also compiles on
//! the host for unit tests.

#![allow(non_camel_case_types)]
#![allow(dead_code)]

use std::ffi::{c_char, c_void};

pub const RETRO_API_VERSION: u32 = 1;

/// `RETRO_ENVIRONMENT_EXPERIMENTAL` is OR-ed into experimental commands.
pub const RETRO_ENVIRONMENT_EXPERIMENTAL: u32 = 0x10000;

// Environment commands.
pub const RETRO_ENVIRONMENT_SET_ROTATION: u32 = 1;
pub const RETRO_ENVIRONMENT_GET_OVERSCAN: u32 = 2;
pub const RETRO_ENVIRONMENT_GET_CAN_DUPE: u32 = 3;
pub const RETRO_ENVIRONMENT_SET_MESSAGE: u32 = 6;
pub const RETRO_ENVIRONMENT_SHUTDOWN: u32 = 7;
pub const RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL: u32 = 8;
pub const RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: u32 = 9;
pub const RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: u32 = 10;
pub const RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS: u32 = 11;
pub const RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK: u32 = 12;
pub const RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: u32 = 13;
pub const RETRO_ENVIRONMENT_SET_HW_RENDER: u32 = 14;
pub const RETRO_ENVIRONMENT_GET_VARIABLE: u32 = 15;
pub const RETRO_ENVIRONMENT_SET_VARIABLES: u32 = 16;
pub const RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: u32 = 17;
pub const RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: u32 = 18;
pub const RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE: u32 = 23;
pub const RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES: u32 = 24;
pub const RETRO_ENVIRONMENT_GET_LOG_INTERFACE: u32 = 27;
pub const RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: u32 = 31;
pub const RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: u32 = 32;
pub const RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO: u32 = 34;
pub const RETRO_ENVIRONMENT_SET_CONTROLLER_INFO: u32 = 35;
pub const RETRO_ENVIRONMENT_SET_MEMORY_MAPS: u32 = 36 | RETRO_ENVIRONMENT_EXPERIMENTAL;
pub const RETRO_ENVIRONMENT_SET_GEOMETRY: u32 = 37;
pub const RETRO_ENVIRONMENT_GET_LANGUAGE: u32 = 39;
pub const RETRO_ENVIRONMENT_GET_CURRENT_SOFTWARE_FRAMEBUFFER: u32 =
    40 | RETRO_ENVIRONMENT_EXPERIMENTAL;
pub const RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE: u32 = 41 | RETRO_ENVIRONMENT_EXPERIMENTAL;
pub const RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS: u32 = 42;
pub const RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS: u32 = 44;
pub const RETRO_ENVIRONMENT_GET_VFS_INTERFACE: u32 = 45;
pub const RETRO_ENVIRONMENT_GET_LED_INTERFACE: u32 = 46;
pub const RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE: u32 = 47;
pub const RETRO_ENVIRONMENT_GET_FASTFORWARDING: u32 = 49;
pub const RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE: u32 = 50;
pub const RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: u32 = 51;
pub const RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: u32 = 52;
pub const RETRO_ENVIRONMENT_SET_CORE_OPTIONS: u32 = 53;
pub const RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: u32 = 54;
pub const RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: u32 = 56;
pub const RETRO_ENVIRONMENT_GET_DISK_CONTROL_INTERFACE_VERSION: u32 = 57;
pub const RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE: u32 = 58;
pub const RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION: u32 = 59;
pub const RETRO_ENVIRONMENT_SET_MESSAGE_EXT: u32 = 60;
pub const RETRO_ENVIRONMENT_GET_INPUT_MAX_USERS: u32 = 61;
pub const RETRO_ENVIRONMENT_SET_AUDIO_BUFFER_STATUS_CALLBACK: u32 = 62;
pub const RETRO_ENVIRONMENT_SET_FASTFORWARDING_OVERRIDE: u32 = 64;
pub const RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2: u32 = 67;
pub const RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: u32 = 68;

pub const RETRO_DEVICE_JOYPAD: u32 = 1;
pub const RETRO_DEVICE_KEYBOARD: u32 = 3;
pub const RETRO_DEVICE_ANALOG: u32 = 5;
pub const RETRO_DEVICE_MASK: u32 = 0xff;

pub const RETRO_DEVICE_ID_JOYPAD_B: u32 = 0;
pub const RETRO_DEVICE_ID_JOYPAD_Y: u32 = 1;
pub const RETRO_DEVICE_ID_JOYPAD_SELECT: u32 = 2;
pub const RETRO_DEVICE_ID_JOYPAD_START: u32 = 3;
pub const RETRO_DEVICE_ID_JOYPAD_UP: u32 = 4;
pub const RETRO_DEVICE_ID_JOYPAD_DOWN: u32 = 5;
pub const RETRO_DEVICE_ID_JOYPAD_LEFT: u32 = 6;
pub const RETRO_DEVICE_ID_JOYPAD_RIGHT: u32 = 7;
pub const RETRO_DEVICE_ID_JOYPAD_A: u32 = 8;
pub const RETRO_DEVICE_ID_JOYPAD_X: u32 = 9;
pub const RETRO_DEVICE_ID_JOYPAD_L: u32 = 10;
pub const RETRO_DEVICE_ID_JOYPAD_R: u32 = 11;
pub const RETRO_DEVICE_ID_JOYPAD_L2: u32 = 12;
pub const RETRO_DEVICE_ID_JOYPAD_R2: u32 = 13;
pub const RETRO_DEVICE_ID_JOYPAD_L3: u32 = 14;
pub const RETRO_DEVICE_ID_JOYPAD_R3: u32 = 15;
pub const RETRO_DEVICE_ID_JOYPAD_MASK: u32 = 256;

pub const RETRO_REGION_NTSC: u32 = 0;

/// Pixel formats reported through `RETRO_ENVIRONMENT_SET_PIXEL_FORMAT`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u32)]
pub enum PixelFormat {
    /// 0RGB1555, native endian. The libretro default when a core forgets to
    /// declare a format.
    Rgb1555 = 0,
    /// XRGB8888, native endian.
    Xrgb8888 = 1,
    /// RGB565, native endian.
    Rgb565 = 2,
}

impl PixelFormat {
    pub fn from_raw(raw: u32) -> Option<Self> {
        match raw {
            0 => Some(Self::Rgb1555),
            1 => Some(Self::Xrgb8888),
            2 => Some(Self::Rgb565),
            _ => None,
        }
    }

    pub fn bytes_per_pixel(self) -> usize {
        match self {
            Self::Rgb1555 | Self::Rgb565 => 2,
            Self::Xrgb8888 => 4,
        }
    }
}

#[repr(C)]
pub struct retro_system_info {
    pub library_name: *const c_char,
    pub library_version: *const c_char,
    pub valid_extensions: *const c_char,
    pub need_fullpath: bool,
    pub block_extract: bool,
}

#[repr(C)]
pub struct retro_game_info {
    pub path: *const c_char,
    pub data: *const c_void,
    pub size: usize,
    pub meta: *const c_char,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct retro_game_geometry {
    pub base_width: u32,
    pub base_height: u32,
    pub max_width: u32,
    pub max_height: u32,
    pub aspect_ratio: f32,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct retro_system_timing {
    pub fps: f64,
    pub sample_rate: f64,
}

impl Default for retro_system_timing {
    fn default() -> Self {
        Self {
            fps: 60.0,
            sample_rate: 48000.0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct retro_system_av_info {
    pub geometry: retro_game_geometry,
    pub timing: retro_system_timing,
}

#[repr(C)]
pub struct retro_variable {
    pub key: *const c_char,
    pub value: *const c_char,
}

#[repr(C)]
pub struct retro_message {
    pub msg: *const c_char,
    pub frames: u32,
}

/// `retro_log_printf_t` is printf-style. The Android build formats the
/// arguments in `csrc/log_shim.c`; other targets use a plain message callback.
pub type retro_log_printf_t = unsafe extern "C" fn(level: u32, fmt: *const c_char, ...);

#[repr(C)]
pub struct retro_log_callback {
    pub log: Option<retro_log_printf_t>,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct retro_keyboard_callback {
    pub callback: Option<unsafe extern "C" fn(down: bool, keycode: u32, character: u32)>,
}

#[repr(C)]
pub struct retro_input_descriptor {
    pub port: u32,
    pub device: u32,
    pub index: u32,
    pub id: u32,
    pub description: *const c_char,
}

pub type retro_environment_t = unsafe extern "C" fn(cmd: u32, data: *mut c_void) -> bool;
pub type retro_video_refresh_t =
    unsafe extern "C" fn(data: *const c_void, width: u32, height: u32, pitch: usize);
pub type retro_audio_sample_t = unsafe extern "C" fn(left: i16, right: i16);
pub type retro_audio_sample_batch_t =
    unsafe extern "C" fn(data: *const i16, frames: usize) -> usize;
pub type retro_input_poll_t = unsafe extern "C" fn();
pub type retro_input_state_t =
    unsafe extern "C" fn(port: u32, device: u32, index: u32, id: u32) -> i16;

/// Resolved `retro_*` entry points of a loaded core.
#[derive(Clone, Copy)]
pub struct CoreFns {
    pub init: unsafe extern "C" fn(),
    pub deinit: unsafe extern "C" fn(),
    pub api_version: unsafe extern "C" fn() -> u32,
    pub get_system_info: unsafe extern "C" fn(*mut retro_system_info),
    pub get_system_av_info: unsafe extern "C" fn(*mut retro_system_av_info),
    pub set_controller_port_device: unsafe extern "C" fn(u32, u32),
    pub reset: unsafe extern "C" fn(),
    pub run: unsafe extern "C" fn(),
    pub serialize_size: unsafe extern "C" fn() -> usize,
    pub serialize: unsafe extern "C" fn(*mut c_void, usize) -> bool,
    pub unserialize: unsafe extern "C" fn(*const c_void, usize) -> bool,
    pub cheat_reset: Option<unsafe extern "C" fn()>,
    pub cheat_set: Option<unsafe extern "C" fn(u32, bool, *const c_char)>,
    pub load_game: unsafe extern "C" fn(*const retro_game_info) -> bool,
    pub unload_game: unsafe extern "C" fn(),
    pub get_region: unsafe extern "C" fn() -> u32,
    pub set_environment: unsafe extern "C" fn(retro_environment_t),
    pub set_video_refresh: unsafe extern "C" fn(retro_video_refresh_t),
    pub set_audio_sample: unsafe extern "C" fn(retro_audio_sample_t),
    pub set_audio_sample_batch: unsafe extern "C" fn(retro_audio_sample_batch_t),
    pub set_input_poll: unsafe extern "C" fn(retro_input_poll_t),
    pub set_input_state: unsafe extern "C" fn(retro_input_state_t),
}
