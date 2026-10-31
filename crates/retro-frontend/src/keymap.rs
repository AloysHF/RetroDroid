//! Android key codes to libretro (`RETROK_*`) mapping.
//!
//! Only keys that make sense on the systems in this family are mapped; the
//! Android back button is handled by the UI, never forwarded to a core.

pub const RETROK_BACKSPACE: u32 = 8;
pub const RETROK_TAB: u32 = 9;
pub const RETROK_RETURN: u32 = 13;
pub const RETROK_ESCAPE: u32 = 27;
pub const RETROK_SPACE: u32 = 32;
pub const RETROK_UP: u32 = 273;
pub const RETROK_DOWN: u32 = 274;
pub const RETROK_RIGHT: u32 = 275;
pub const RETROK_LEFT: u32 = 276;
pub const RETROK_INSERT: u32 = 277;
pub const RETROK_HOME: u32 = 278;
pub const RETROK_END: u32 = 279;
pub const RETROK_PAGEUP: u32 = 280;
pub const RETROK_PAGEDOWN: u32 = 281;
pub const RETROK_F1: u32 = 282;
pub const RETROK_RSHIFT: u32 = 303;
pub const RETROK_LSHIFT: u32 = 304;
pub const RETROK_RCTRL: u32 = 305;
pub const RETROK_LCTRL: u32 = 306;
pub const RETROK_RALT: u32 = 307;
pub const RETROK_LALT: u32 = 308;

/// Number of key slots tracked for `RETRO_DEVICE_KEYBOARD` queries.
pub const KEY_COUNT: usize = 512;

pub fn keycode_to_retro(android: i32) -> Option<u32> {
    // Android `KeyEvent` constants.
    const KEYCODE_A: i32 = 29;
    const KEYCODE_Z: i32 = 54;
    const KEYCODE_0: i32 = 7;
    const KEYCODE_9: i32 = 16;
    const KEYCODE_COMMA: i32 = 55;
    const KEYCODE_PERIOD: i32 = 56;
    const KEYCODE_GRAVE: i32 = 68;
    const KEYCODE_MINUS: i32 = 69;
    const KEYCODE_EQUALS: i32 = 70;
    const KEYCODE_LEFT_BRACKET: i32 = 71;
    const KEYCODE_RIGHT_BRACKET: i32 = 72;
    const KEYCODE_BACKSLASH: i32 = 73;
    const KEYCODE_SEMICOLON: i32 = 74;
    const KEYCODE_APOSTROPHE: i32 = 75;
    const KEYCODE_SLASH: i32 = 76;
    const KEYCODE_SPACE: i32 = 62;
    const KEYCODE_ENTER: i32 = 66;
    const KEYCODE_DEL: i32 = 67;
    const KEYCODE_TAB: i32 = 61;
    const KEYCODE_ESCAPE: i32 = 111;
    const KEYCODE_SHIFT_LEFT: i32 = 59;
    const KEYCODE_SHIFT_RIGHT: i32 = 60;
    const KEYCODE_CTRL_LEFT: i32 = 113;
    const KEYCODE_CTRL_RIGHT: i32 = 114;
    const KEYCODE_ALT_LEFT: i32 = 57;
    const KEYCODE_ALT_RIGHT: i32 = 58;
    const KEYCODE_DPAD_UP: i32 = 19;
    const KEYCODE_DPAD_DOWN: i32 = 20;
    const KEYCODE_DPAD_LEFT: i32 = 21;
    const KEYCODE_DPAD_RIGHT: i32 = 22;
    const KEYCODE_MOVE_HOME: i32 = 122;
    const KEYCODE_MOVE_END: i32 = 123;
    const KEYCODE_PAGE_UP: i32 = 92;
    const KEYCODE_PAGE_DOWN: i32 = 93;
    const KEYCODE_INSERT: i32 = 124;
    const KEYCODE_F1: i32 = 131;
    const KEYCODE_F12: i32 = 142;

    match android {
        KEYCODE_A..=KEYCODE_Z => Some(97 + (android - KEYCODE_A) as u32),
        KEYCODE_0..=KEYCODE_9 => Some(48 + (android - KEYCODE_0) as u32),
        KEYCODE_F1..=KEYCODE_F12 => Some(RETROK_F1 + (android - KEYCODE_F1) as u32),
        KEYCODE_COMMA => Some(44),
        KEYCODE_PERIOD => Some(46),
        KEYCODE_GRAVE => Some(96),
        KEYCODE_MINUS => Some(45),
        KEYCODE_EQUALS => Some(61),
        KEYCODE_LEFT_BRACKET => Some(91),
        KEYCODE_RIGHT_BRACKET => Some(93),
        KEYCODE_BACKSLASH => Some(92),
        KEYCODE_SEMICOLON => Some(59),
        KEYCODE_APOSTROPHE => Some(39),
        KEYCODE_SLASH => Some(47),
        KEYCODE_SPACE => Some(RETROK_SPACE),
        KEYCODE_ENTER => Some(RETROK_RETURN),
        KEYCODE_DEL => Some(RETROK_BACKSPACE),
        KEYCODE_TAB => Some(RETROK_TAB),
        KEYCODE_ESCAPE => Some(RETROK_ESCAPE),
        KEYCODE_SHIFT_LEFT => Some(RETROK_LSHIFT),
        KEYCODE_SHIFT_RIGHT => Some(RETROK_RSHIFT),
        KEYCODE_CTRL_LEFT => Some(RETROK_LCTRL),
        KEYCODE_CTRL_RIGHT => Some(RETROK_RCTRL),
        KEYCODE_ALT_LEFT => Some(RETROK_LALT),
        KEYCODE_ALT_RIGHT => Some(RETROK_RALT),
        KEYCODE_DPAD_UP => Some(RETROK_UP),
        KEYCODE_DPAD_DOWN => Some(RETROK_DOWN),
        KEYCODE_DPAD_LEFT => Some(RETROK_LEFT),
        KEYCODE_DPAD_RIGHT => Some(RETROK_RIGHT),
        KEYCODE_MOVE_HOME => Some(RETROK_HOME),
        KEYCODE_MOVE_END => Some(RETROK_END),
        KEYCODE_PAGE_UP => Some(RETROK_PAGEUP),
        KEYCODE_PAGE_DOWN => Some(RETROK_PAGEDOWN),
        KEYCODE_INSERT => Some(RETROK_INSERT),
        _ => None,
    }
}

/// Printable character produced by a key press, honouring shift for letters
/// and the digit row. Returns 0 for non-character keys.
pub fn character_for(retro: u32, shift: bool) -> u32 {
    match retro {
        97..=122 => {
            let base = if shift { retro - 32 } else { retro };
            base
        }
        48..=57 => {
            if !shift {
                retro
            } else if retro == 48 {
                b')' as u32
            } else {
                b"!@#$%^&*"[(retro - 49) as usize] as u32
            }
        }
        RETROK_SPACE => 32,
        RETROK_RETURN => 13,
        RETROK_BACKSPACE => 8,
        RETROK_TAB => 9,
        RETROK_ESCAPE => 27,
        44 => {
            if shift {
                60
            } else {
                44
            }
        }
        45 => {
            if shift {
                95
            } else {
                45
            }
        }
        46 => {
            if shift {
                62
            } else {
                46
            }
        }
        47 => {
            if shift {
                63
            } else {
                47
            }
        }
        59 => {
            if shift {
                58
            } else {
                59
            }
        }
        61 => {
            if shift {
                43
            } else {
                61
            }
        }
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_letters_and_digits() {
        assert_eq!(keycode_to_retro(29), Some(97)); // A
        assert_eq!(keycode_to_retro(54), Some(122)); // Z
        assert_eq!(keycode_to_retro(7), Some(48)); // 0
        assert_eq!(keycode_to_retro(16), Some(57)); // 9
    }

    #[test]
    fn maps_navigation_and_modifiers() {
        assert_eq!(keycode_to_retro(19), Some(RETROK_UP));
        assert_eq!(keycode_to_retro(59), Some(RETROK_LSHIFT));
        assert_eq!(keycode_to_retro(66), Some(RETROK_RETURN));
        assert_eq!(keycode_to_retro(67), Some(RETROK_BACKSPACE));
        assert_eq!(keycode_to_retro(4), None); // back button stays in the UI
    }

    #[test]
    fn characters_respect_shift() {
        assert_eq!(character_for(97, false), 'a' as u32);
        assert_eq!(character_for(97, true), 'A' as u32);
        assert_eq!(character_for(49, true), '!' as u32);
        assert_eq!(character_for(44, true), '<' as u32);
        assert_eq!(character_for(RETROK_UP, true), 0);
    }
}
