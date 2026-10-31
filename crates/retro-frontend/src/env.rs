//! Implementation of `RETRO_ENVIRONMENT_*` callbacks.
//!
//! The core talks to RetroDroid exclusively through this function. State is
//! kept in a process-wide mutex because libretro exposes the environment as a
//! plain C function pointer with no user-data argument.

use std::collections::BTreeMap;
use std::ffi::{c_char, c_void, CStr, CString};
use std::ptr;
use std::sync::{Mutex, MutexGuard, OnceLock};

use crate::ffi::*;
use crate::options::{self, CoreOption};

pub const RETRO_LANGUAGE_ENGLISH: u32 = 0;

#[derive(Clone, Copy)]
pub struct AvInfo {
    pub width: u32,
    pub height: u32,
    pub fps: f64,
    pub sample_rate: f64,
}

impl Default for AvInfo {
    fn default() -> Self {
        Self {
            width: 0,
            height: 0,
            fps: 60.0,
            sample_rate: 48000.0,
        }
    }
}

pub struct EnvState {
    pub system_dir: CString,
    pub save_dir: CString,
    pub pixel_format: PixelFormat,
    pub options: Vec<CoreOption>,
    pub pending_options: BTreeMap<String, String>,
    pub option_update: bool,
    pub messages: Vec<String>,
    pub keyboard_cb: Option<retro_keyboard_callback>,
    pub av: AvInfo,
    pub shutdown: bool,
    pub rotation: u16,
    pub language: u32,
}

impl Default for EnvState {
    fn default() -> Self {
        Self {
            system_dir: CString::new("/").expect("literal"),
            save_dir: CString::new("/").expect("literal"),
            pixel_format: PixelFormat::Rgb1555,
            options: Vec::new(),
            pending_options: BTreeMap::new(),
            option_update: false,
            messages: Vec::new(),
            keyboard_cb: None,
            av: AvInfo::default(),
            shutdown: false,
            rotation: 0,
            language: RETRO_LANGUAGE_ENGLISH,
        }
    }
}

static ENV: OnceLock<Mutex<EnvState>> = OnceLock::new();

fn env() -> &'static Mutex<EnvState> {
    ENV.get_or_init(|| Mutex::new(EnvState::default()))
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    // A panic in a callback must not disable the emulator for good; recover the
    // guarded state instead of panicking across the C ABI.
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Reset the per-session state before a core is loaded.
pub fn reset(system_dir: &str, save_dir: &str, saved_options: &BTreeMap<String, String>) {
    let mut state = lock(env());
    let mut next = EnvState::default();
    next.system_dir = CString::new(system_dir).unwrap_or_else(|_| CString::new("/").expect("literal"));
    next.save_dir = CString::new(save_dir).unwrap_or_else(|_| CString::new("/").expect("literal"));
    next.pending_options = saved_options.clone();
    next.language = state.language;
    *state = next;
}

pub fn av_info() -> AvInfo {
    lock(env()).av
}

pub fn set_av_info(info: AvInfo) {
    lock(env()).av = info;
}

pub fn pixel_format_and_rotation() -> (PixelFormat, u16) {
    let state = lock(env());
    (state.pixel_format, state.rotation)
}

pub fn set_rotation(rotation: u16) {
    lock(env()).rotation = rotation % 360;
}

pub fn set_language(language: u32) {
    lock(env()).language = language;
}

pub fn shutdown_requested() -> bool {
    lock(env()).shutdown
}

pub fn clear_shutdown() {
    lock(env()).shutdown = false;
}

/// Read and clear the shutdown request raised by the core.
pub fn take_shutdown() -> bool {
    let mut state = lock(env());
    std::mem::take(&mut state.shutdown)
}

pub fn take_messages() -> Vec<String> {
    std::mem::take(&mut lock(env()).messages)
}

pub fn push_message(message: impl Into<String>) {
    let mut state = lock(env());
    state.messages.push(message.into());
    let excess = state.messages.len().saturating_sub(16);
    if excess > 0 {
        state.messages.drain(..excess);
    }
}

pub fn keyboard_callback() -> Option<retro_keyboard_callback> {
    lock(env()).keyboard_cb
}

/// Re-apply a persisted selection at runtime; the core learns about it through
/// `GET_VARIABLE_UPDATE` on its next frame.
pub fn set_option(key: &str, value: &str) -> bool {
    let mut state = lock(env());
    let Some(option) = state.options.iter_mut().find(|o| o.key == key) else {
        return false;
    };
    if !option.select(value) {
        return false;
    }
    state.option_update = true;
    true
}

pub fn options_json() -> String {
    options::to_json(&lock(env()).options)
}

fn emit_log(level: u32, text: &str) {
    match level {
        0 => log::debug!("[core] {text}"),
        1 => log::info!("[core] {text}"),
        2 => log::warn!("[core] {text}"),
        _ => log::error!("[core] {text}"),
    }
}

#[cfg(target_os = "android")]
unsafe extern "C" fn log_sink(level: u32, text: *const c_char) {
    if text.is_null() {
        return;
    }
    // SAFETY: the shim always hands over a NUL-terminated buffer.
    let text = unsafe { CStr::from_ptr(text) }.to_string_lossy();
    emit_log(level, &text);
}

/// Formats the core's printf-style log calls on the C side and forwards the
/// result to [`log_sink`].
#[cfg(target_os = "android")]
mod log_shim {
    use std::ffi::c_char;
    use std::sync::Once;

    use super::{log_sink, retro_log_printf_t};

    unsafe extern "C" {
        fn retrodroid_log_printf(level: u32, fmt: *const c_char, ...);
        fn retrodroid_log_set_sink(callback: Option<unsafe extern "C" fn(u32, *const c_char)>);
    }

    pub fn callback() -> retro_log_printf_t {
        static SET: Once = Once::new();
        SET.call_once(|| unsafe { retrodroid_log_set_sink(Some(log_sink)) });
        retrodroid_log_printf
    }
}

fn log_callback() -> Option<retro_log_printf_t> {
    #[cfg(target_os = "android")]
    {
        Some(log_shim::callback())
    }
    #[cfg(not(target_os = "android"))]
    {
        // Host builds (tests) have no C shim; the core simply logs nowhere.
        None
    }
}

/// The environment callback handed to the core.
///
/// # Safety
/// `cmd`/`data` follow the libretro contract; every pointer is validated
/// before use and unknown commands return `false`.
pub unsafe extern "C" fn environment(cmd: u32, data: *mut c_void) -> bool {
    let cmd = cmd & !RETRO_ENVIRONMENT_EXPERIMENTAL;
    match cmd {
        RETRO_ENVIRONMENT_SET_PIXEL_FORMAT => {
            if data.is_null() {
                return false;
            }
            match PixelFormat::from_raw(*(data as *const u32)) {
                Some(format) => {
                    lock(env()).pixel_format = format;
                    log::info!("core pixel format: {format:?}");
                    true
                }
                None => false,
            }
        }
        RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY | RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY => {
            if data.is_null() {
                return false;
            }
            let state = lock(env());
            let value = if cmd == RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY {
                state.system_dir.as_ptr()
            } else {
                state.save_dir.as_ptr()
            };
            *(data as *mut *const c_char) = value;
            true
        }
        RETRO_ENVIRONMENT_SET_VARIABLES => {
            if data.is_null() {
                return false;
            }
            let variables = data as *const retro_variable;
            let mut parsed = Vec::new();
            for index in 0..1024 {
                let entry = &*variables.add(index);
                if entry.key.is_null() {
                    break;
                }
                let key = CStr::from_ptr(entry.key).to_string_lossy();
                if entry.value.is_null() {
                    continue;
                }
                let value = CStr::from_ptr(entry.value).to_string_lossy();
                if let Some(option) = CoreOption::parse(&key, &value) {
                    parsed.push(option);
                }
            }
            let mut state = lock(env());
            options::apply_selections(&mut parsed, &state.pending_options);
            state.options = parsed;
            true
        }
        RETRO_ENVIRONMENT_GET_VARIABLE => {
            if data.is_null() {
                return false;
            }
            let variable = &mut *(data as *mut retro_variable);
            if variable.key.is_null() {
                return false;
            }
            let state = lock(env());
            match options::find_value(&state.options, CStr::from_ptr(variable.key)) {
                Some(value) => {
                    variable.value = value.as_ptr();
                    true
                }
                None => {
                    variable.value = ptr::null();
                    false
                }
            }
        }
        RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE => {
            if data.is_null() {
                return false;
            }
            let mut state = lock(env());
            *(data as *mut bool) = state.option_update;
            state.option_update = false;
            true
        }
        RETRO_ENVIRONMENT_SET_MESSAGE => {
            if data.is_null() {
                return false;
            }
            let message = &*(data as *const retro_message);
            if !message.msg.is_null() {
                let text = CStr::from_ptr(message.msg).to_string_lossy().into_owned();
                log::warn!("core message: {text}");
                push_message(text);
            }
            true
        }
        RETRO_ENVIRONMENT_SET_MESSAGE_EXT => {
            if data.is_null() {
                return false;
            }
            #[repr(C)]
            struct MessageExt {
                msg: *const c_char,
                _duration: u32,
                _priority: u32,
                _level: u32,
                _target: u32,
            }
            let message = &*(data as *const MessageExt);
            if !message.msg.is_null() {
                let text = CStr::from_ptr(message.msg).to_string_lossy().into_owned();
                log::warn!("core message: {text}");
                push_message(text);
            }
            true
        }
        RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK => {
            if data.is_null() {
                return false;
            }
            lock(env()).keyboard_cb = Some(*(data as *const retro_keyboard_callback));
            true
        }
        RETRO_ENVIRONMENT_SET_GEOMETRY => {
            if data.is_null() {
                return false;
            }
            let geometry = &*(data as *const retro_game_geometry);
            if geometry.base_width == 0 || geometry.base_height == 0 {
                return false;
            }
            let mut state = lock(env());
            state.av.width = geometry.base_width;
            state.av.height = geometry.base_height;
            true
        }
        RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO => {
            if data.is_null() {
                return false;
            }
            let info = &*(data as *const retro_system_av_info);
            let mut state = lock(env());
            if info.geometry.base_width > 0 && info.geometry.base_height > 0 {
                state.av.width = info.geometry.base_width;
                state.av.height = info.geometry.base_height;
            }
            if info.timing.fps > 0.0 {
                state.av.fps = info.timing.fps;
            }
            if info.timing.sample_rate > 0.0 {
                state.av.sample_rate = info.timing.sample_rate;
            }
            true
        }
        RETRO_ENVIRONMENT_GET_CAN_DUPE => {
            if data.is_null() {
                return false;
            }
            *(data as *mut bool) = true;
            true
        }
        RETRO_ENVIRONMENT_SHUTDOWN => {
            lock(env()).shutdown = true;
            true
        }
        RETRO_ENVIRONMENT_GET_LOG_INTERFACE => {
            if data.is_null() {
                return false;
            }
            *(data as *mut retro_log_callback) = retro_log_callback {
                log: log_callback(),
            };
            true
        }
        RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL => true,
        RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS => true,
        RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME => true,
        RETRO_ENVIRONMENT_SET_CONTROLLER_INFO => true,
        RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS => true,
        RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS => true,
        RETRO_ENVIRONMENT_GET_LANGUAGE => {
            if data.is_null() {
                return false;
            }
            *(data as *mut u32) = lock(env()).language;
            true
        }
        RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION => {
            if data.is_null() {
                return false;
            }
            *(data as *mut u32) = 1;
            true
        }
        RETRO_ENVIRONMENT_GET_INPUT_MAX_USERS => {
            if data.is_null() {
                return false;
            }
            *(data as *mut u32) = 1;
            true
        }
        RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE => {
            if data.is_null() {
                return false;
            }
            *(data as *mut f32) = 60.0;
            true
        }
        // Deliberately unsupported: hardware rendering, VFS, rumble, sensors,
        // core-option v2 and runtime core downloads. Returning `false` makes
        // cores take their software paths.
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reset_installs_directories_and_selections() {
        let mut saved = BTreeMap::new();
        saved.insert("key".to_owned(), "b".to_owned());
        reset("/system", "/save", &saved);

        let state = lock(env());
        assert_eq!(state.system_dir.to_str().unwrap(), "/system");
        assert_eq!(state.save_dir.to_str().unwrap(), "/save");
        assert_eq!(state.pending_options.get("key").map(String::as_str), Some("b"));
        assert_eq!(state.pixel_format, PixelFormat::Rgb1555);
    }

    #[test]
    fn messages_are_capped() {
        reset("/s", "/v", &BTreeMap::new());
        take_messages();
        for index in 0..40 {
            push_message(format!("message {index}"));
        }
        let messages = take_messages();
        assert_eq!(messages.len(), 16);
        assert_eq!(messages.last().unwrap(), "message 39");
    }
}
