//! `ANativeWindow` output.
//!
//! Frames are converted to RGB565 by `blit`, copied into the surface buffer
//! acquired through the NDK and posted. All calls happen on the emulation
//! thread, except `from_surface`/`release` which are driven by JNI.

/// Opaque `ANativeWindow` handle, represented as an address so it can be stored
/// in plain data structures.
pub type WindowPtr = usize;

#[cfg(not(target_os = "android"))]
mod imp {
    use super::WindowPtr;

    pub unsafe fn from_surface(
        _env: *mut std::ffi::c_void,
        _surface: *mut std::ffi::c_void,
    ) -> WindowPtr {
        0
    }

    pub unsafe fn release(_window: WindowPtr) {}

    pub fn post(window: WindowPtr, pixels: &[u16], width: usize, height: usize) {
        let _ = (window, pixels, width, height);
    }
}

#[cfg(target_os = "android")]
mod imp {
    use super::WindowPtr;
    use crate::blit;
    use std::ffi::c_void;
    use std::sync::atomic::{AtomicU64, Ordering};

    #[repr(C)]
    struct ANativeWindow {
        _private: [u8; 0],
    }

    #[repr(C)]
    struct ANativeWindow_Buffer {
        width: i32,
        height: i32,
        stride: i32,
        format: i32,
        bits: *mut c_void,
        reserved: [u32; 6],
    }

    const WINDOW_FORMAT_RGB_565: i32 = 4;

    #[link(name = "android")]
    extern "C" {
        fn ANativeWindow_fromSurface(env: *mut c_void, surface: *mut c_void) -> *mut ANativeWindow;
        fn ANativeWindow_release(window: *mut ANativeWindow);
        fn ANativeWindow_setBuffersGeometry(
            window: *mut ANativeWindow,
            width: i32,
            height: i32,
            format: i32,
        ) -> i32;
        fn ANativeWindow_lock(
            window: *mut ANativeWindow,
            buffer: *mut ANativeWindow_Buffer,
            dirty: *const c_void,
        ) -> i32;
        fn ANativeWindow_unlockAndPost(window: *mut ANativeWindow) -> i32;
    }

    /// Last geometry configured on the surface, packed as `(w << 32) | h`.
    static LAST_GEOMETRY: AtomicU64 = AtomicU64::new(0);

    pub unsafe fn from_surface(env: *mut c_void, surface: *mut c_void) -> WindowPtr {
        ANativeWindow_fromSurface(env, surface) as WindowPtr
    }

    pub unsafe fn release(window: WindowPtr) {
        if window != 0 {
            ANativeWindow_release(window as *mut ANativeWindow);
        }
        LAST_GEOMETRY.store(0, Ordering::Relaxed);
    }

    pub fn post(window: WindowPtr, pixels: &[u16], width: usize, height: usize) {
        if window == 0 || width == 0 || height == 0 {
            return;
        }
        let window = window as *mut ANativeWindow;
        let geometry = ((width as u64) << 32) | height as u64;
        unsafe {
            if LAST_GEOMETRY.load(Ordering::Relaxed) != geometry {
                ANativeWindow_setBuffersGeometry(
                    window,
                    width as i32,
                    height as i32,
                    WINDOW_FORMAT_RGB_565,
                );
                LAST_GEOMETRY.store(geometry, Ordering::Relaxed);
            }

            let mut buffer: ANativeWindow_Buffer = std::mem::zeroed();
            if ANativeWindow_lock(window, &mut buffer, std::ptr::null()) != 0 {
                return;
            }
            let buffer_width = buffer.width.max(0) as usize;
            let buffer_height = buffer.height.max(0) as usize;
            let stride = buffer.stride.max(0) as usize;
            if !buffer.bits.is_null() && buffer_width > 0 && buffer_height > 0 && stride > 0 {
                blit::blit_to_buffer(
                    buffer.bits as *mut u16,
                    stride,
                    buffer_width,
                    buffer_height,
                    pixels,
                    width,
                    height,
                );
            }
            ANativeWindow_unlockAndPost(window);
        }
    }
}

#[cfg_attr(not(target_os = "android"), allow(unused_imports))]
pub use imp::{from_surface, post, release};
