//! Fixed-size stereo audio ring buffer.
//!
//! The core produces samples inside `retro_run`, the Kotlin side consumes them
//! from an `AudioTrack` thread. The ring never allocates after construction: a
//! slow consumer drops the oldest audio instead of stalling emulation.

use std::sync::{Mutex, MutexGuard, OnceLock};

/// Two seconds of stereo audio at 48 kHz.
const CAPACITY_FRAMES: usize = 48_000 * 2;

/// Interleaved stereo samples, indexed by frame.
pub struct AudioRing {
    data: Vec<i16>,
    head: usize,
    len: usize,
}

impl AudioRing {
    /// `frames` is the capacity in stereo frames.
    pub fn new(frames: usize) -> Self {
        let frames = frames.max(1);
        Self {
            data: vec![0; frames * 2],
            head: 0,
            len: 0,
        }
    }

    #[cfg(test)]
    pub fn capacity_frames(&self) -> usize {
        self.data.len() / 2
    }

    /// Number of complete stereo frames currently buffered.
    #[cfg(test)]
    pub fn available_frames(&self) -> usize {
        self.len / 2
    }

    pub fn clear(&mut self) {
        self.head = 0;
        self.len = 0;
    }

    /// Append interleaved stereo samples (`samples.len()` must be even).
    pub fn push(&mut self, samples: &[i16]) {
        let capacity = self.data.len();
        if samples.len() >= capacity {
            // Only the newest `capacity` samples can survive.
            let tail = &samples[samples.len() - capacity..];
            self.data.copy_from_slice(tail);
            self.head = 0;
            self.len = capacity;
            return;
        }

        let tail = (self.head + self.len) % capacity;
        let first = (capacity - tail).min(samples.len());
        self.data[tail..tail + first].copy_from_slice(&samples[..first]);
        let rest = samples.len() - first;
        if rest > 0 {
            self.data[..rest].copy_from_slice(&samples[first..]);
        }

        self.len += samples.len();
        if self.len > capacity {
            // Overwrote the oldest audio: advance the read head.
            let overflow = self.len - capacity;
            self.head = (self.head + overflow) % capacity;
            self.len = capacity;
        }
    }

    /// Copy up to `out.len()` samples and consume them. Returns the count.
    pub fn pop(&mut self, out: &mut [i16]) -> usize {
        let count = out.len().min(self.len);
        if count == 0 {
            return 0;
        }
        let capacity = self.data.len();
        let first = (capacity - self.head).min(count);
        out[..first].copy_from_slice(&self.data[self.head..self.head + first]);
        let rest = count - first;
        if rest > 0 {
            out[first..count].copy_from_slice(&self.data[..rest]);
        }
        self.head = (self.head + count) % capacity;
        self.len -= count;
        count
    }
}

static AUDIO: OnceLock<Mutex<AudioRing>> = OnceLock::new();

pub fn audio() -> &'static Mutex<AudioRing> {
    AUDIO.get_or_init(|| Mutex::new(AudioRing::new(CAPACITY_FRAMES)))
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub fn reset() {
    lock(audio()).clear();
}

/// Consume interleaved stereo samples for the audio thread.
pub fn read(dst: &mut [i16]) -> usize {
    lock(audio()).pop(dst)
}

pub unsafe extern "C" fn audio_sample(left: i16, right: i16) {
    lock(audio()).push(&[left, right]);
}

pub unsafe extern "C" fn audio_sample_batch(data: *const i16, frames: usize) -> usize {
    if data.is_null() || frames == 0 {
        return frames;
    }
    let samples = std::slice::from_raw_parts(data, frames * 2);
    lock(audio()).push(samples);
    frames
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn push_pop_roundtrip() {
        let mut ring = AudioRing::new(4);
        ring.push(&[1, 2, 3, 4]);
        assert_eq!(ring.available_frames(), 2);

        let mut out = [0i16; 4];
        assert_eq!(ring.pop(&mut out), 4);
        assert_eq!(out, [1, 2, 3, 4]);
        assert_eq!(ring.available_frames(), 0);
        assert_eq!(ring.pop(&mut out), 0);
    }

    #[test]
    fn partial_and_wrapped_reads() {
        let mut ring = AudioRing::new(4);
        ring.push(&[1, 2, 3, 4, 5, 6]);
        assert_eq!(ring.available_frames(), 3);

        let mut small = [0i16; 4];
        assert_eq!(ring.pop(&mut small), 4);
        assert_eq!(small, [1, 2, 3, 4]);

        ring.push(&[7, 8, 9, 10]);
        let mut rest = [0i16; 4];
        assert_eq!(ring.pop(&mut rest), 4);
        assert_eq!(rest, [5, 6, 7, 8]);
    }

    #[test]
    fn overflow_drops_oldest() {
        let mut ring = AudioRing::new(2);
        ring.push(&[1, 2, 3, 4, 5, 6]);
        assert_eq!(ring.available_frames(), 2);
        let mut out = [0i16; 4];
        assert_eq!(ring.pop(&mut out), 4);
        assert_eq!(out, [3, 4, 5, 6]);
    }

    #[test]
    fn oversized_push_keeps_newest() {
        let mut ring = AudioRing::new(2);
        ring.push(&[1, 2, 3, 4, 5, 6, 7, 8]);
        let mut out = [0i16; 4];
        assert_eq!(ring.pop(&mut out), 4);
        assert_eq!(out, [5, 6, 7, 8]);
    }
}
