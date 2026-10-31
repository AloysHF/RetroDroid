//! Input state shared between JNI setters and the libretro callbacks.

use std::sync::{Mutex, MutexGuard, OnceLock};

use crate::ffi::*;
use crate::keymap;

#[derive(Clone, Copy)]
pub struct KeyEvent {
    pub down: bool,
    pub key: u32,
    pub character: u32,
}

pub struct InputState {
    buttons: u32,
    keys: [bool; keymap::KEY_COUNT],
}

impl Default for InputState {
    fn default() -> Self {
        Self {
            buttons: 0,
            keys: [false; keymap::KEY_COUNT],
        }
    }
}

static INPUT: OnceLock<Mutex<InputState>> = OnceLock::new();
static KEY_EVENTS: OnceLock<Mutex<Vec<KeyEvent>>> = OnceLock::new();

fn input() -> &'static Mutex<InputState> {
    INPUT.get_or_init(|| Mutex::new(InputState::default()))
}

fn events() -> &'static Mutex<Vec<KeyEvent>> {
    KEY_EVENTS.get_or_init(|| Mutex::new(Vec::new()))
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub fn reset() {
    *lock(input()) = InputState::default();
    lock(events()).clear();
}

/// RetroPad buttons as a bitmask using `RETRO_DEVICE_ID_JOYPAD_*` positions.
pub fn set_buttons(mask: u32) {
    lock(input()).buttons = mask & 0xffff;
}

/// Track a physical/on-screen keyboard key.
pub fn set_key(android_keycode: i32, down: bool) {
    let Some(retro) = keymap::keycode_to_retro(android_keycode) else {
        return;
    };
    let character = {
        let mut state = lock(input());
        if let Some(slot) = state.keys.get_mut(retro as usize) {
            *slot = down;
        }
        let shift = state.keys[keymap::RETROK_LSHIFT as usize]
            || state.keys[keymap::RETROK_RSHIFT as usize];
        if down {
            keymap::character_for(retro, shift)
        } else {
            0
        }
    };

    let mut queue = lock(events());
    queue.push(KeyEvent {
        down,
        key: retro,
        character,
    });
    let excess = queue.len().saturating_sub(64);
    if excess > 0 {
        queue.drain(..excess);
    }
}

pub fn take_key_events() -> Vec<KeyEvent> {
    std::mem::take(&mut *lock(events()))
}

pub unsafe extern "C" fn input_poll() {}

pub unsafe extern "C" fn input_state(port: u32, device: u32, index: u32, id: u32) -> i16 {
    if port != 0 {
        return 0;
    }
    match device & RETRO_DEVICE_MASK {
        RETRO_DEVICE_JOYPAD => {
            let state = lock(input());
            if id == RETRO_DEVICE_ID_JOYPAD_MASK {
                state.buttons as i16
            } else if id < 16 {
                ((state.buttons >> id) & 1) as i16
            } else {
                0
            }
        }
        RETRO_DEVICE_KEYBOARD => {
            if index != 0 {
                return 0;
            }
            let state = lock(input());
            state.keys.get(id as usize).copied().unwrap_or(false) as i16
        }
        _ => 0,
    }
}
