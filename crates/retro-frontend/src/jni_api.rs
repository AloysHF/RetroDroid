//! JNI surface consumed by `com.aloyshf.retrodroid.core.NativeCore`.

use std::ffi::c_void;
use std::panic::AssertUnwindSafe;

use jni::objects::{JByteArray, JClass, JObject, JShortArray, JString};
use jni::sys::{jboolean, jbyteArray, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::{Env, EnvUnowned};

use crate::audio;
use crate::env;
use crate::input;
use crate::session;
use crate::video;
use crate::window;

fn log_panic(info: &std::panic::PanicHookInfo<'_>) {
    log::error!("panic: {info}");
}

fn init_logging() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("RetroDroid"),
    );
    let _ = std::panic::take_hook();
    std::panic::set_hook(Box::new(log_panic));
}

fn read_string(env: &mut Env<'_>, value: &JString<'_>) -> Result<String, String> {
    env.get_string(value)
        .map(Into::into)
        .map_err(|error| format!("Invalid string argument: {error}"))
}

fn normalize_rotation(rotation: i32) -> u16 {
    match rotation.rem_euclid(360) {
        90 => 90,
        180 => 180,
        270 => 270,
        _ => 0,
    }
}

fn guard<T>(fallback: T, body: impl FnOnce() -> Result<T, String>) -> T {
    match std::panic::catch_unwind(AssertUnwindSafe(body)) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => {
            session::set_error(error);
            fallback
        }
        Err(_) => {
            session::set_error("Internal error in the emulator frontend.");
            fallback
        }
    }
}

fn with_env<'local, T>(
    unowned: &mut EnvUnowned<'local>,
    body: impl FnOnce(&mut Env<'local>) -> T,
) -> T {
    let mut value = None;
    let _ = unowned.with_env_no_catch(|env| -> Result<(), jni::errors::Error> {
        value = Some(body(env));
        Ok(())
    });
    value.expect("with_env_no_catch always runs its body")
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeInit(
    _env: EnvUnowned,
    _class: JClass,
) {
    let _ = std::panic::catch_unwind(|| {
        init_logging();
        log::info!("RetroDroid frontend {} ready", env!("CARGO_PKG_VERSION"));
    });
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSetLanguage(
    _env: EnvUnowned,
    _class: JClass,
    language: jint,
) {
    env::set_language(language.max(0) as u32);
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeLoadCore<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    core_path: JString<'local>,
    content_path: JString<'local>,
    system_dir: JString<'local>,
    save_dir: JString<'local>,
    options_json: JString<'local>,
) -> jboolean {
    with_env(&mut env, |env| {
        guard(JNI_FALSE, || {
            let core_path = read_string(env, &core_path)?;
            let content_path = if content_path.is_null() {
                None
            } else {
                Some(read_string(env, &content_path)?)
            };
            let system_dir = read_string(env, &system_dir)?;
            let save_dir = read_string(env, &save_dir)?;
            let options_json = read_string(env, &options_json)?;
            session::load(
                &core_path,
                content_path.as_deref(),
                &system_dir,
                &save_dir,
                &options_json,
            )?;
            Ok(JNI_TRUE)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeUnload(
    _env: EnvUnowned,
    _class: JClass,
) {
    let _ = std::panic::catch_unwind(|| session::unload());
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeStart<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    surface: JObject<'local>,
    rotation: jint,
) -> jboolean {
    with_env(&mut env, |env| {
        guard(JNI_FALSE, || {
            env::set_rotation(normalize_rotation(rotation));
            let raw_env: *mut c_void = env.get_raw().cast();
            let window = unsafe { window::from_surface(raw_env, surface.as_raw().cast()) };
            if window == 0 {
                return Err("Could not acquire the video surface.".to_owned());
            }
            if let Err(error) = session::start(window) {
                unsafe { window::release(window) };
                return Err(error);
            }
            Ok(JNI_TRUE)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeStop(
    _env: EnvUnowned,
    _class: JClass,
) {
    let _ = std::panic::catch_unwind(session::stop);
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativePause(
    _env: EnvUnowned,
    _class: JClass,
    paused: jboolean,
) {
    let _ = std::panic::catch_unwind(|| session::set_paused(paused != JNI_FALSE));
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeReset(
    _env: EnvUnowned,
    _class: JClass,
) -> jboolean {
    guard(JNI_FALSE, || {
        session::reset()?;
        Ok(JNI_TRUE)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSetRotation(
    _env: EnvUnowned,
    _class: JClass,
    rotation: jint,
) {
    env::set_rotation(normalize_rotation(rotation));
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeAvInfo(
    mut env: EnvUnowned,
    _class: JClass,
) -> jni::sys::jintArray {
    with_env(&mut env, |env| {
        let av = env::av_info();
        let values = [
            av.width as jint,
            av.height as jint,
            (av.fps * 1000.0).round() as jint,
            av.sample_rate.round() as jint,
        ];
        match env.new_int_array(4) {
            Ok(array) => {
                if env.set_int_array_region(&array, 0, &values).is_ok() {
                    array.into_raw()
                } else {
                    std::ptr::null_mut()
                }
            }
            Err(_) => std::ptr::null_mut(),
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeOutputSize(
    mut env: EnvUnowned,
    _class: JClass,
) -> jni::sys::jintArray {
    with_env(&mut env, |env| {
        let (width, height) = video::output_size();
        let values = [width as jint, height as jint];
        match env.new_int_array(2) {
            Ok(array) => {
                if env.set_int_array_region(&array, 0, &values).is_ok() {
                    array.into_raw()
                } else {
                    std::ptr::null_mut()
                }
            }
            Err(_) => std::ptr::null_mut(),
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSetButtons(
    _env: EnvUnowned,
    _class: JClass,
    mask: jint,
) {
    input::set_buttons(mask as u32);
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSetKey(
    _env: EnvUnowned,
    _class: JClass,
    android_keycode: jint,
    down: jboolean,
) {
    input::set_key(android_keycode, down != JNI_FALSE);
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSetOption<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    key: JString<'local>,
    value: JString<'local>,
) -> jboolean {
    with_env(&mut env, |env| {
        guard(JNI_FALSE, || {
            let key = read_string(env, &key)?;
            let value = read_string(env, &value)?;
            Ok(if env::set_option(&key, &value) {
                JNI_TRUE
            } else {
                JNI_FALSE
            })
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeOptions(
    mut env: EnvUnowned,
    _class: JClass,
) -> jstring {
    with_env(&mut env, |env| {
        let json = env::options_json();
        match env.new_string(json) {
            Ok(string) => string.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSystemInfo(
    mut env: EnvUnowned,
    _class: JClass,
) -> jstring {
    with_env(&mut env, |env| {
        let json = session::system_info_json();
        match env.new_string(json) {
            Ok(string) => string.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeSerialize(
    mut env: EnvUnowned,
    _class: JClass,
) -> jbyteArray {
    with_env(&mut env, |env| {
        guard(std::ptr::null_mut(), || {
            let data = session::serialize()?;
            env.byte_array_from_slice(&data)
                .map(JByteArray::into_raw)
                .map_err(|error| format!("Failed to copy the save state: {error}"))
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeUnserialize(
    mut env: EnvUnowned,
    _class: JClass,
    data: JByteArray,
) -> jboolean {
    with_env(&mut env, |env| {
        guard(JNI_FALSE, || {
            let data = env
                .convert_byte_array(&data)
                .map_err(|error| format!("Failed to read the save state: {error}"))?;
            session::unserialize(&data)?;
            Ok(JNI_TRUE)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativeReadAudio(
    mut env: EnvUnowned,
    _class: JClass,
    dst: JShortArray,
    frames: jint,
) -> jint {
    with_env(&mut env, |env| {
        guard(0, || {
            let frames = frames.max(0) as usize;
            if frames == 0 {
                return Ok(0);
            }
            let length = env
                .get_array_length(&dst)
                .map_err(|error| format!("Invalid audio buffer: {error}"))?
                as usize;
            let want = (frames * 2).min(length);
            if want == 0 {
                return Ok(0);
            }
            let mut buffer = vec![0i16; want];
            let read = audio::read(&mut buffer);
            if read > 0 {
                env.set_short_array_region(&dst, 0, &buffer[..read])
                    .map_err(|error| format!("Failed to fill the audio buffer: {error}"))?;
            }
            Ok((read / 2) as jint)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_aloyshf_retrodroid_core_NativeCore_nativePollEvents(
    mut env: EnvUnowned,
    _class: JClass,
) -> jstring {
    with_env(&mut env, |env| {
        let json = serde_json::json!({
            "messages": env::take_messages(),
            "shutdown": env::take_shutdown(),
            "running": session::is_running(),
            "loaded": session::is_loaded(),
            "error": session::take_error(),
        })
        .to_string();
        match env.new_string(json) {
            Ok(string) => string.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
}
