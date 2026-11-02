//! Core loading and the emulation loop.

use std::collections::BTreeMap;
use std::ffi::{c_void, CStr, CString};
use std::panic::AssertUnwindSafe;
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use libloading::Library;

use crate::audio;
use crate::env::{self, AvInfo};
use crate::ffi::*;
use crate::input;
use crate::video;
use crate::window;

struct Session {
    _library: Library,
    fns: CoreFns,
    /// Held while any `retro_*` entry point runs, so save states requested from
    /// the UI cannot interleave with a frame.
    core_lock: Mutex<()>,
    info_json: String,
}

struct RunControl {
    stop: AtomicBool,
    paused: AtomicBool,
}

struct RunState {
    control: Arc<RunControl>,
    thread: JoinHandle<()>,
    window: window::WindowPtr,
}

static SESSION: OnceLock<Mutex<Option<Arc<Session>>>> = OnceLock::new();
static RUN: OnceLock<Mutex<Option<RunState>>> = OnceLock::new();
static LAST_ERROR: OnceLock<Mutex<Option<String>>> = OnceLock::new();

fn session_slot() -> &'static Mutex<Option<Arc<Session>>> {
    SESSION.get_or_init(|| Mutex::new(None))
}

fn run_slot() -> &'static Mutex<Option<RunState>> {
    RUN.get_or_init(|| Mutex::new(None))
}

fn error_slot() -> &'static Mutex<Option<String>> {
    LAST_ERROR.get_or_init(|| Mutex::new(None))
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub fn set_error(message: impl Into<String>) {
    let message = message.into();
    log::error!("{message}");
    *lock(error_slot()) = Some(message);
}

pub fn take_error() -> Option<String> {
    lock(error_slot()).take()
}

pub fn is_running() -> bool {
    lock(run_slot()).is_some()
}

pub fn is_loaded() -> bool {
    lock(session_slot()).is_some()
}

fn current_session() -> Option<Arc<Session>> {
    lock(session_slot()).clone()
}

/// Load a core and its content. `content_path` is `None` for firmware-only
/// systems that boot without a game. Any previously loaded session is unloaded
/// first.
pub fn load(
    core_path: &str,
    content_path: Option<&str>,
    system_dir: &str,
    save_dir: &str,
    options_json: &str,
) -> Result<(), String> {
    unload();
    *lock(error_slot()) = None;

    let saved = parse_saved_options(options_json);
    env::reset(system_dir, save_dir, &saved);
    video::reset();
    audio::reset();
    input::reset();

    let library = unsafe { Library::new(core_path) }
        .map_err(|e| format!("Failed to load core {core_path}: {e}"))?;
    let fns = unsafe { resolve(&library) }
        .map_err(|e| format!("Core {core_path} is not a usable libretro core: {e}"))?;

    let system_info_json = unsafe { read_system_info(&fns) };

    unsafe {
        (fns.set_environment)(env::environment);
        (fns.set_video_refresh)(video::video_refresh);
        (fns.set_audio_sample)(audio::audio_sample);
        (fns.set_audio_sample_batch)(audio::audio_sample_batch);
        (fns.set_input_poll)(input::input_poll);
        (fns.set_input_state)(input::input_state);
        (fns.init)();
    }

    let path = match content_path {
        Some(content) => Some(
            CString::new(content).map_err(|_| format!("Unsupported content path: {content}"))?,
        ),
        None => None,
    };
    let game_info = retro_game_info {
        path: path.as_ref().map_or(ptr::null(), |path| path.as_ptr()),
        data: ptr::null(),
        size: 0,
        meta: ptr::null(),
    };

    let loaded = std::panic::catch_unwind(AssertUnwindSafe(|| unsafe {
        (fns.load_game)(if content_path.is_some() {
            &game_info as *const retro_game_info
        } else {
            ptr::null()
        })
    }))
    .unwrap_or(false);

    if !loaded {
        let messages = env::take_messages();
        unsafe {
            (fns.deinit)();
        }
        let detail = messages.join("; ");
        let target = content_path.unwrap_or("the firmware");
        return Err(if detail.is_empty() {
            format!("The core could not load {target}.")
        } else {
            detail
        });
    }

    let mut av = retro_system_av_info::default();
    unsafe {
        (fns.get_system_av_info)(&mut av);
    }
    env::set_av_info(AvInfo {
        width: av.geometry.base_width,
        height: av.geometry.base_height,
        fps: if av.timing.fps.is_finite() && av.timing.fps > 1.0 {
            av.timing.fps
        } else {
            60.0
        },
        sample_rate: if av.timing.sample_rate.is_finite() && av.timing.sample_rate >= 8000.0 {
            av.timing.sample_rate
        } else {
            48_000.0
        },
    });

    let session = Arc::new(Session {
        _library: library,
        fns,
        core_lock: Mutex::new(()),
        info_json: system_info_json,
    });
    *lock(session_slot()) = Some(session);

    let av = env::av_info();
    log::info!(
        "Loaded {} ({}x{}, {:.2} fps, {} Hz)",
        content_path.unwrap_or("firmware-only session"),
        av.width,
        av.height,
        av.fps,
        av.sample_rate
    );
    Ok(())
}

/// Unload the current core. Stops the emulation thread first, then lets the
/// core release its state before `dlclose`.
pub fn unload() {
    stop();
    let mut slot = lock(session_slot());
    let Some(session) = slot.take() else {
        return;
    };
    {
        let _guard = lock(&session.core_lock);
        unsafe {
            std::panic::catch_unwind(AssertUnwindSafe(|| {
                (session.fns.unload_game)();
                (session.fns.deinit)();
            }))
            .ok();
        }
    }
    drop(session);
    audio::reset();
    log::info!("Core unloaded");
}

/// Start the emulation loop, rendering into `window`.
pub fn start(window: window::WindowPtr) -> Result<(), String> {
    stop();
    if window == 0 {
        return Err("The video surface is not ready.".to_owned());
    }
    let session = current_session().ok_or_else(|| "No core is loaded.".to_owned())?;

    audio::reset();
    env::clear_shutdown();

    let control = Arc::new(RunControl {
        stop: AtomicBool::new(false),
        paused: AtomicBool::new(false),
    });
    let thread_control = control.clone();
    let thread = thread::Builder::new()
        .name("retro-emulation".to_owned())
        .spawn(move || run_loop(session, thread_control, window))
        .map_err(|e| format!("Failed to start the emulation thread: {e}"))?;

    *lock(run_slot()) = Some(RunState {
        control,
        thread,
        window,
    });
    Ok(())
}

/// Stop the emulation loop and release the surface.
pub fn stop() {
    let run = lock(run_slot()).take();
    if let Some(run) = run {
        run.control.stop.store(true, Ordering::Relaxed);
        let _ = run.thread.join();
        unsafe { window::release(run.window) };
        audio::reset();
    }
}

pub fn set_paused(paused: bool) {
    if let Some(run) = lock(run_slot()).as_ref() {
        run.control.paused.store(paused, Ordering::Relaxed);
    }
    if paused {
        audio::reset();
    }
}

pub fn reset() -> Result<(), String> {
    let session = current_session().ok_or_else(|| "No core is loaded.".to_owned())?;
    let _guard = lock(&session.core_lock);
    unsafe { (session.fns.reset)() };
    Ok(())
}

pub fn serialize() -> Result<Vec<u8>, String> {
    let session = current_session().ok_or_else(|| "No core is loaded.".to_owned())?;
    let _guard = lock(&session.core_lock);
    let size = unsafe { (session.fns.serialize_size)() };
    if size == 0 {
        return Err("This core does not support save states.".to_owned());
    }
    let mut buffer = vec![0u8; size];
    let ok = unsafe { (session.fns.serialize)(buffer.as_mut_ptr() as *mut c_void, size) };
    if ok {
        Ok(buffer)
    } else {
        Err("The core failed to produce a save state.".to_owned())
    }
}

pub fn unserialize(data: &[u8]) -> Result<(), String> {
    if data.is_empty() {
        return Err("The save state is empty.".to_owned());
    }
    let session = current_session().ok_or_else(|| "No core is loaded.".to_owned())?;
    let _guard = lock(&session.core_lock);
    let ok = unsafe { (session.fns.unserialize)(data.as_ptr() as *const c_void, data.len()) };
    if ok {
        Ok(())
    } else {
        Err("The save state was rejected by the core (wrong system or version).".to_owned())
    }
}

pub fn system_info_json() -> String {
    current_session()
        .map(|session| session.info_json.clone())
        .unwrap_or_else(|| "null".to_owned())
}

pub fn av_info_json() -> String {
    let av = env::av_info();
    serde_json::json!({
        "width": av.width,
        "height": av.height,
        "fps": av.fps,
        "sampleRate": av.sample_rate,
    })
    .to_string()
}

fn parse_saved_options(json: &str) -> BTreeMap<String, String> {
    let mut map = BTreeMap::new();
    let Ok(serde_json::Value::Object(object)) = serde_json::from_str::<serde_json::Value>(json)
    else {
        return map;
    };
    for (key, value) in object {
        if let Some(value) = value.as_str() {
            map.insert(key, value.to_owned());
        }
    }
    map
}

fn run_loop(session: Arc<Session>, control: Arc<RunControl>, window: window::WindowPtr) {
    let av = env::av_info();
    let frame_time = Duration::from_secs_f64(1.0 / av.fps);
    let mut next = Instant::now();

    loop {
        if control.stop.load(Ordering::Relaxed) {
            break;
        }
        if control.paused.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(8));
            next = Instant::now();
            continue;
        }

        dispatch_key_events();

        let panicked = {
            let _guard = lock(&session.core_lock);
            std::panic::catch_unwind(AssertUnwindSafe(|| unsafe { (session.fns.run)() })).is_err()
        };
        if panicked {
            set_error("The core crashed while running; the session was stopped.");
            break;
        }
        if env::shutdown_requested() {
            env::clear_shutdown();
            break;
        }

        post_frame(window);

        next += frame_time;
        let now = Instant::now();
        if next > now {
            thread::sleep(next - now);
        } else if now.duration_since(next) > Duration::from_millis(250) {
            // Fell far behind (app suspended, slow device): resynchronize
            // instead of running a burst of frames.
            next = now;
        }
    }

    log::info!("Emulation loop stopped");
}

fn dispatch_key_events() {
    let events = input::take_key_events();
    if events.is_empty() {
        return;
    }
    let Some(callback) = env::keyboard_callback().and_then(|kb| kb.callback) else {
        return;
    };
    for event in events {
        unsafe { callback(event.down, event.key, event.character) };
    }
}

fn post_frame(window: window::WindowPtr) {
    let mut state = crate::video::video()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if !state.dirty {
        return;
    }
    state.dirty = false;
    window::post(window, &state.pixels, state.width, state.height);
}

unsafe fn resolve(library: &Library) -> Result<CoreFns, String> {
    macro_rules! required {
        ($name:literal, $ty:ty) => {
            *library
                .get::<$ty>(concat!($name, "\0").as_bytes())
                .map_err(|e| format!("missing {}: {e}", $name))?
        };
    }
    macro_rules! optional {
        ($name:literal, $ty:ty) => {
            library
                .get::<$ty>(concat!($name, "\0").as_bytes())
                .ok()
                .map(|symbol| *symbol)
        };
    }

    Ok(CoreFns {
        init: required!("retro_init", unsafe extern "C" fn()),
        deinit: required!("retro_deinit", unsafe extern "C" fn()),
        api_version: required!("retro_api_version", unsafe extern "C" fn() -> u32),
        get_system_info: required!(
            "retro_get_system_info",
            unsafe extern "C" fn(*mut retro_system_info)
        ),
        get_system_av_info: required!(
            "retro_get_system_av_info",
            unsafe extern "C" fn(*mut retro_system_av_info)
        ),
        set_controller_port_device: required!(
            "retro_set_controller_port_device",
            unsafe extern "C" fn(u32, u32)
        ),
        reset: required!("retro_reset", unsafe extern "C" fn()),
        run: required!("retro_run", unsafe extern "C" fn()),
        serialize_size: required!("retro_serialize_size", unsafe extern "C" fn() -> usize),
        serialize: required!(
            "retro_serialize",
            unsafe extern "C" fn(*mut c_void, usize) -> bool
        ),
        unserialize: required!(
            "retro_unserialize",
            unsafe extern "C" fn(*const c_void, usize) -> bool
        ),
        cheat_reset: optional!("retro_cheat_reset", unsafe extern "C" fn()),
        cheat_set: optional!(
            "retro_cheat_set",
            unsafe extern "C" fn(u32, bool, *const std::ffi::c_char)
        ),
        load_game: required!(
            "retro_load_game",
            unsafe extern "C" fn(*const retro_game_info) -> bool
        ),
        unload_game: required!("retro_unload_game", unsafe extern "C" fn()),
        get_region: required!("retro_get_region", unsafe extern "C" fn() -> u32),
        set_environment: required!(
            "retro_set_environment",
            unsafe extern "C" fn(retro_environment_t)
        ),
        set_video_refresh: required!(
            "retro_set_video_refresh",
            unsafe extern "C" fn(retro_video_refresh_t)
        ),
        set_audio_sample: required!(
            "retro_set_audio_sample",
            unsafe extern "C" fn(retro_audio_sample_t)
        ),
        set_audio_sample_batch: required!(
            "retro_set_audio_sample_batch",
            unsafe extern "C" fn(retro_audio_sample_batch_t)
        ),
        set_input_poll: required!(
            "retro_set_input_poll",
            unsafe extern "C" fn(retro_input_poll_t)
        ),
        set_input_state: required!(
            "retro_set_input_state",
            unsafe extern "C" fn(retro_input_state_t)
        ),
    })
}

unsafe fn read_system_info(fns: &CoreFns) -> String {
    let mut info: retro_system_info = std::mem::zeroed();
    (fns.get_system_info)(&mut info);
    let text = |ptr: *const std::ffi::c_char| -> String {
        if ptr.is_null() {
            String::new()
        } else {
            CStr::from_ptr(ptr).to_string_lossy().into_owned()
        }
    };
    serde_json::json!({
        "name": text(info.library_name),
        "version": text(info.library_version),
        "extensions": text(info.valid_extensions),
        "needFullpath": info.need_fullpath,
        "apiVersion": (fns.api_version)(),
    })
    .to_string()
}

#[cfg(test)]
mod host_tests {
    use super::*;

    /// Drives a host build of a core without a window. Requires:
    ///
    /// ```text
    /// RETRODROID_TEST_CORE=.../native32emu.dll
    /// RETRODROID_TEST_CONTENT=.../native32_game/FHUI.smf
    /// cargo test -p retro-frontend -- --ignored host_core_smoke
    /// ```
    #[test]
    #[ignore = "requires a host-built core and game content"]
    fn host_core_smoke() {
        let core = std::env::var("RETRODROID_TEST_CORE").expect("RETRODROID_TEST_CORE");
        let content = std::env::var("RETRODROID_TEST_CONTENT").expect("RETRODROID_TEST_CONTENT");
        let root = std::env::temp_dir().join("retrodroid-host-smoke");
        std::fs::create_dir_all(&root).expect("temp dir");
        let root = root.to_str().expect("utf-8 temp path");

        load(&core, Some(&content), root, root, "{}").expect("core should load the content");
        let session = current_session().expect("session");

        for _ in 0..120 {
            let _guard = lock(&session.core_lock);
            unsafe { (session.fns.run)() };
        }

        let (width, height) = crate::video::output_size();
        assert!(width > 0 && height > 0, "the core produced no video");

        let state = serialize().expect("serialize");
        assert!(!state.is_empty());
        reset().expect("reset");
        unserialize(&state).expect("unserialize");

        unload();
        assert!(!is_loaded());
    }
}
