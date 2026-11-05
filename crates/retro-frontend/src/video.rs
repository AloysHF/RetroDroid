//! Video callback: converts the core frame into the staging buffer used by the
//! window blitter.

use std::ffi::c_void;
use std::sync::{Mutex, MutexGuard, OnceLock};

use crate::blit;
use crate::env;

#[derive(Default)]
pub struct VideoState {
    pub pixels: Vec<u16>,
    pub width: usize,
    pub height: usize,
    pub dirty: bool,
    pub frames: u64,
}

static VIDEO: OnceLock<Mutex<VideoState>> = OnceLock::new();

pub fn video() -> &'static Mutex<VideoState> {
    VIDEO.get_or_init(|| Mutex::new(VideoState::default()))
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub fn reset() {
    let mut state = lock(video());
    state.width = 0;
    state.height = 0;
    state.dirty = false;
    state.frames = 0;
}

/// Size of the most recent frame after rotation.
pub fn output_size() -> (usize, usize) {
    let state = lock(video());
    (state.width, state.height)
}

/// Snapshot of the most recent frame as opaque ARGB pixels, ready for
/// `Bitmap.createBitmap`. Returns `None` before the core has produced video.
pub fn frame_argb() -> Option<(usize, usize, Vec<u32>)> {
    let state = lock(video());
    if state.width == 0 || state.height == 0 {
        return None;
    }
    let pixel_count = state.width.checked_mul(state.height)?;
    if state.pixels.len() < pixel_count {
        return None;
    }
    let pixels = state.pixels[..pixel_count]
        .iter()
        .map(|&pixel| blit::rgb565_to_argb(pixel))
        .collect();
    Some((state.width, state.height, pixels))
}

pub unsafe extern "C" fn video_refresh(data: *const c_void, width: u32, height: u32, pitch: usize) {
    let (format, rotation) = env::pixel_format_and_rotation();
    let mut state = lock(video());

    // A null pointer means "same frame as before" (dupe frame); keep the
    // staging buffer and let the loop re-post it.
    if data.is_null() {
        state.dirty = true;
        return;
    }
    if width == 0 || height == 0 || pitch == 0 {
        return;
    }

    let width = width as usize;
    let height = height as usize;
    let Some(len) = pitch.checked_mul(height) else {
        return;
    };
    let src = std::slice::from_raw_parts(data as *const u8, len);
    let (out_w, out_h) = blit::convert_frame(
        src,
        width,
        height,
        pitch,
        format,
        rotation,
        &mut state.pixels,
    );
    state.width = out_w;
    state.height = out_h;
    state.dirty = true;
    state.frames += 1;
}
