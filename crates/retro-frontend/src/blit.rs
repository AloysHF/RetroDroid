//! Pixel conversion and rotation for the libretro video callback.

use crate::ffi::PixelFormat;

/// Convert one core frame into packed RGB565, rotating it clockwise.
///
/// `src` is the core frame with `pitch` bytes per row. The destination is
/// resized to the rotated dimensions; returns `(width, height)` of `dst`.
pub fn convert_frame(
    src: &[u8],
    width: usize,
    height: usize,
    pitch: usize,
    format: PixelFormat,
    rotation: u16,
    dst: &mut Vec<u16>,
) -> (usize, usize) {
    let rotation = rotation % 360;
    let (out_w, out_h) = match rotation {
        90 | 270 => (height, width),
        _ => (width, height),
    };
    dst.resize(out_w.saturating_mul(out_h), 0);

    if width == 0 || height == 0 {
        return (out_w, out_h);
    }

    let bpp = format.bytes_per_pixel();
    let row_bytes = width * bpp;
    if pitch < row_bytes || src.len() < pitch * height {
        // Truncated or malformed frame: leave a black screen instead of
        // reading out of bounds.
        dst.fill(0);
        return (out_w, out_h);
    }

    for sy in 0..height {
        let row = &src[sy * pitch..sy * pitch + row_bytes];
        for sx in 0..width {
            let pixel = match format {
                PixelFormat::Rgb565 => u16::from_ne_bytes([row[sx * 2], row[sx * 2 + 1]]),
                PixelFormat::Rgb1555 => {
                    let v = u16::from_ne_bytes([row[sx * 2], row[sx * 2 + 1]]);
                    expand_1555(v)
                }
                PixelFormat::Xrgb8888 => {
                    let v = u32::from_ne_bytes([
                        row[sx * 4],
                        row[sx * 4 + 1],
                        row[sx * 4 + 2],
                        row[sx * 4 + 3],
                    ]);
                    pack_8888(v)
                }
            };

            let (dx, dy) = match rotation {
                90 => (height - 1 - sy, sx),
                180 => (width - 1 - sx, height - 1 - sy),
                270 => (sy, width - 1 - sx),
                _ => (sx, sy),
            };
            dst[dy * out_w + dx] = pixel;
        }
    }

    (out_w, out_h)
}

fn pack_8888(v: u32) -> u16 {
    let r = ((v >> 19) & 0x1f) as u16;
    let g = ((v >> 10) & 0x3f) as u16;
    let b = ((v >> 3) & 0x1f) as u16;
    (r << 11) | (g << 5) | b
}

fn expand_1555(v: u16) -> u16 {
    let r = (v >> 10) & 0x1f;
    let g = (v >> 5) & 0x1f;
    let b = v & 0x1f;
    (r << 11) | (((g << 1) | (g >> 4)) << 5) | b
}

/// Copy packed RGB565 into an `ANativeWindow` buffer, scaling nearest-neighbour
/// if the buffer size does not match (for example while a surface is being
/// recreated).
///
/// # Safety
/// `bits` must point to a writable buffer of `stride * buffer_height` pixels.
pub unsafe fn blit_to_buffer(
    bits: *mut u16,
    stride: usize,
    buffer_width: usize,
    buffer_height: usize,
    src: &[u16],
    src_width: usize,
    src_height: usize,
) {
    if bits.is_null() || src_width == 0 || src_height == 0 {
        return;
    }
    if buffer_width == src_width && buffer_height == src_height {
        for y in 0..src_height {
            let dst = bits.add(y * stride);
            std::ptr::copy_nonoverlapping(src.as_ptr().add(y * src_width), dst, src_width);
        }
        return;
    }

    for y in 0..buffer_height {
        let sy = y * src_height / buffer_height;
        let src_row = src.as_ptr().add(sy.min(src_height - 1) * src_width);
        let dst_row = bits.add(y * stride);
        for x in 0..buffer_width {
            let sx = x * src_width / buffer_width;
            *dst_row.add(x) = *src_row.add(sx.min(src_width - 1));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rgb565(r: u8, g: u8, b: u8) -> u16 {
        (((r as u16) >> 3) << 11) | (((g as u16) >> 2) << 5) | ((b as u16) >> 3)
    }

    #[test]
    fn xrgb8888_conversion() {
        let px = 0x00FF_8040u32;
        let src = px.to_ne_bytes();
        let mut out = Vec::new();
        let (w, h) = convert_frame(&src, 1, 1, 4, PixelFormat::Xrgb8888, 0, &mut out);
        assert_eq!((w, h), (1, 1));
        assert_eq!(out[0], rgb565(0xFF, 0x80, 0x40));
    }

    #[test]
    fn rgb565_passthrough_honours_pitch() {
        let row = [rgb565(255, 0, 0), rgb565(0, 255, 0)];
        // 2 bytes of padding after each row.
        let mut padded = Vec::new();
        for _ in 0..2 {
            for value in row {
                padded.extend_from_slice(&value.to_ne_bytes());
            }
            padded.extend_from_slice(&[0xAA, 0xBB]);
        }

        let mut out = Vec::new();
        let (w, h) = convert_frame(&padded, 2, 2, 6, PixelFormat::Rgb565, 0, &mut out);
        assert_eq!((w, h), (2, 2));
        assert_eq!(out, [rgb565(255, 0, 0), rgb565(0, 255, 0), rgb565(255, 0, 0), rgb565(0, 255, 0)]);
    }

    #[test]
    fn rotation_90_maps_corners() {
        // 2x1 frame: [A, B]
        let a = rgb565(255, 0, 0);
        let b = rgb565(0, 0, 255);
        let mut src = Vec::new();
        src.extend_from_slice(&a.to_ne_bytes());
        src.extend_from_slice(&b.to_ne_bytes());

        let mut out = Vec::new();
        let (w, h) = convert_frame(&src, 2, 1, 4, PixelFormat::Rgb565, 90, &mut out);
        assert_eq!((w, h), (1, 2));
        // Clockwise: A ends up at the top, B at the bottom.
        assert_eq!(out, [a, b]);
    }

    #[test]
    fn rotation_180_flips() {
        let a = rgb565(255, 0, 0);
        let b = rgb565(0, 255, 0);
        let mut src = Vec::new();
        for value in [a, b] {
            src.extend_from_slice(&value.to_ne_bytes());
        }
        let mut out = Vec::new();
        convert_frame(&src, 2, 1, 4, PixelFormat::Rgb565, 180, &mut out);
        assert_eq!(out, [b, a]);
    }

    #[test]
    fn truncated_frame_is_black() {
        let mut out = Vec::new();
        let (w, h) = convert_frame(&[0u8; 4], 4, 4, 8, PixelFormat::Rgb565, 0, &mut out);
        assert_eq!((w, h), (4, 4));
        assert!(out.iter().all(|&p| p == 0));
    }

    #[test]
    fn nearest_scaling() {
        let src = [1u16, 2, 3, 4];
        let mut dst = [0u16; 8];
        unsafe { blit_to_buffer(dst.as_mut_ptr(), 4, 4, 2, &src, 2, 2) };
        assert_eq!(dst, [1, 1, 2, 2, 3, 3, 4, 4]);
    }
}
