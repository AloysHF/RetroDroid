//! Core option declarations and selections.

use std::collections::BTreeMap;
use std::ffi::{CStr, CString};

/// One `RETRO_ENVIRONMENT_SET_VARIABLES` entry.
///
/// Choices are stored as `CString` so `RETRO_ENVIRONMENT_GET_VARIABLE` can hand
/// the core a stable pointer without allocating per call.
pub struct CoreOption {
    pub key: String,
    pub label: String,
    pub choices: Vec<CString>,
    pub selected: usize,
}

impl CoreOption {
    /// Parse `"Label; choice1|choice2|..."`. Returns `None` for malformed
    /// entries (no key, no choices, or embedded NUL bytes).
    pub fn parse(key: &str, value: &str) -> Option<Self> {
        if key.is_empty() {
            return None;
        }
        let (label, list) = match value.split_once(';') {
            Some((label, list)) => (label.trim(), list),
            None => ("", value),
        };

        let mut choices = Vec::new();
        for raw in list.split('|') {
            let choice = raw.trim();
            if !choice.is_empty() {
                choices.push(CString::new(choice).ok()?);
            }
        }
        if choices.is_empty() {
            return None;
        }

        Some(Self {
            key: key.to_owned(),
            label: label.to_owned(),
            choices,
            selected: 0,
        })
    }

    pub fn selection(&self) -> &str {
        self.choices[self.selected].to_str().unwrap_or_default()
    }

    pub fn select(&mut self, value: &str) -> bool {
        match self.choices.iter().position(|c| c.to_bytes() == value.as_bytes()) {
            Some(index) => {
                self.selected = index;
                true
            }
            None => false,
        }
    }
}

/// Apply persisted selections to freshly declared options.
pub fn apply_selections(options: &mut [CoreOption], saved: &BTreeMap<String, String>) {
    for option in options.iter_mut() {
        if let Some(value) = saved.get(&option.key) {
            option.select(value);
        }
    }
}

/// Serialize the declared options for the settings screen.
pub fn to_json(options: &[CoreOption]) -> String {
    let value = serde_json::json!(options
        .iter()
        .map(|o| serde_json::json!({
            "key": o.key,
            "label": if o.label.is_empty() { &o.key } else { &o.label },
            "values": o.choices.iter().map(|c| c.to_string_lossy()).collect::<Vec<_>>(),
            "selected": o.selection(),
        }))
        .collect::<Vec<_>>());
    value.to_string()
}

/// Look up a value for `GET_VARIABLE`, returning a pointer the core may keep
/// until the next `SET_VARIABLES`.
pub fn find_value<'a>(options: &'a [CoreOption], key: &CStr) -> Option<&'a CStr> {
    let key = key.to_bytes();
    options
        .iter()
        .find(|o| o.key.as_bytes() == key)
        .map(|o| o.choices[o.selected].as_c_str())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_standard_entry() {
        let option =
            CoreOption::parse("native32emu_volume", "Audio Volume; 100|90|80").unwrap();
        assert_eq!(option.label, "Audio Volume");
        assert_eq!(option.choices.len(), 3);
        assert_eq!(option.selection(), "100");
    }

    #[test]
    fn parses_entry_without_label() {
        let option = CoreOption::parse("key", "on|off").unwrap();
        assert_eq!(option.label, "");
        assert_eq!(option.selection(), "on");
    }

    #[test]
    fn skips_empty_choices() {
        let option = CoreOption::parse("key", "Label; a||b|").unwrap();
        assert_eq!(option.choices.len(), 2);
    }

    #[test]
    fn selection_is_validated() {
        let mut option = CoreOption::parse("key", "Label; on|off").unwrap();
        assert!(!option.select("maybe"));
        assert!(option.select("off"));
        assert_eq!(option.selection(), "off");
    }

    #[test]
    fn applies_saved_selections() {
        let mut options = vec![CoreOption::parse("key", "Label; a|b|c").unwrap()];
        let mut saved = BTreeMap::new();
        saved.insert("key".to_owned(), "c".to_owned());
        apply_selections(&mut options, &saved);
        assert_eq!(options[0].selection(), "c");
    }

    #[test]
    fn json_contains_labels_and_values() {
        let options = vec![CoreOption::parse("k", "Volume; 1|2").unwrap()];
        let json = to_json(&options);
        assert!(json.contains("\"label\":\"Volume\""));
        assert!(json.contains("\"selected\":\"1\""));
    }
}
