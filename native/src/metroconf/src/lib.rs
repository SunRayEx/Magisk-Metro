//! metroconf — declarative manifest parser for MagisKube Metro persistent modules.
//!
//! Parses and validates the JSON manifest that describes the user's persistent module set
//! (a nix-like immutable, reproducible declaration). The link manager (Zig + C) drives this
//! crate through a small C ABI so the parsing rules live in exactly one place.

use std::fmt::Write as _;

pub const MANIFEST_VERSION: i64 = 1;

#[derive(Clone, Debug, PartialEq)]
pub struct Entry {
    pub id: String,
    pub name: String,
    pub version: String,
    pub version_code: i64,
    pub enabled: bool,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct Manifest {
    pub version: i64,
    pub updated_at: i64,
    pub modules: Vec<Entry>,
}

impl Manifest {
    pub fn upsert(&mut self, entry: Entry) {
        match self.modules.iter_mut().find(|m| m.id == entry.id) {
            Some(existing) => *existing = entry,
            None => self.modules.push(entry),
        }
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.modules.len();
        self.modules.retain(|m| m.id != id);
        self.modules.len() != before
    }

    pub fn to_json(&self) -> String {
        let mut out = String::new();
        out.push_str("{\n");
        let _ = write!(out, "  \"version\": {},\n", self.version.max(1));
        let _ = write!(out, "  \"updated_at\": {},\n", self.updated_at);
        out.push_str("  \"modules\": [\n");
        for (i, m) in self.modules.iter().enumerate() {
            out.push_str("    {\n");
            let _ = write!(out, "      \"id\": {},\n", json_str(&m.id));
            let _ = write!(out, "      \"name\": {},\n", json_str(&m.name));
            let _ = write!(out, "      \"version\": {},\n", json_str(&m.version));
            let _ = write!(out, "      \"version_code\": {},\n", m.version_code);
            let _ = write!(out, "      \"enabled\": {}", m.enabled);
            out.push_str("\n    }");
            if i + 1 != self.modules.len() {
                out.push(',');
            }
            out.push('\n');
        }
        out.push_str("  ]\n}\n");
        out
    }
}

fn json_str(value: &str) -> String {
    let mut out = String::with_capacity(value.len() + 2);
    out.push('"');
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

// ---------------------------------------------------------------------------
// JSON value parser (dependency-free)
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<Value>),
    Obj(Vec<(String, Value)>),
}

impl Value {
    fn get(&self, key: &str) -> Option<&Value> {
        match self {
            Value::Obj(entries) => entries.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }
}

#[derive(Debug)]
pub struct ParseError {
    pub message: String,
    pub offset: usize,
}

impl ParseError {
    fn new(message: &str, offset: usize) -> ParseError {
        ParseError { message: message.to_string(), offset }
    }
}

struct Parser<'a> {
    src: &'a [u8],
    pos: usize,
}

impl<'a> Parser<'a> {
    fn new(src: &'a str) -> Parser<'a> {
        Parser { src: src.as_bytes(), pos: 0 }
    }

    fn err(&self, message: &str) -> ParseError {
        ParseError::new(message, self.pos)
    }

    fn skip_ws(&mut self) {
        while let Some(&b) = self.src.get(self.pos) {
            if b == b' ' || b == b'\t' || b == b'\n' || b == b'\r' {
                self.pos += 1;
            } else {
                break;
            }
        }
    }

    fn peek(&self) -> Option<u8> {
        self.src.get(self.pos).copied()
    }

    fn expect(&mut self, byte: u8) -> Result<(), ParseError> {
        self.skip_ws();
        if self.peek() == Some(byte) {
            self.pos += 1;
            Ok(())
        } else {
            Err(self.err(&format!("expected '{}'", byte as char)))
        }
    }

    fn literal(&mut self, word: &str, value: Value) -> Result<Value, ParseError> {
        if self.src[self.pos..].starts_with(word.as_bytes()) {
            self.pos += word.len();
            Ok(value)
        } else {
            Err(self.err("invalid literal"))
        }
    }

    fn string(&mut self) -> Result<String, ParseError> {
        self.expect(b'"')?;
        let mut out = String::new();
        loop {
            let byte = self.peek().ok_or_else(|| self.err("unterminated string"))?;
            self.pos += 1;
            match byte {
                b'"' => return Ok(out),
                b'\\' => {
                    let esc = self.peek().ok_or_else(|| self.err("bad escape"))?;
                    self.pos += 1;
                    match esc {
                        b'"' => out.push('"'),
                        b'\\' => out.push('\\'),
                        b'/' => out.push('/'),
                        b'b' => out.push('\u{8}'),
                        b'f' => out.push('\u{c}'),
                        b'n' => out.push('\n'),
                        b'r' => out.push('\r'),
                        b't' => out.push('\t'),
                        b'u' => {
                            let hex = self.hex4()?;
                            let ch = if (0xD800..0xDC00).contains(&hex) {
                                // high surrogate; expect a low surrogate
                                if self.peek() == Some(b'\\') {
                                    self.pos += 1;
                                    if self.peek() == Some(b'u') {
                                        self.pos += 1;
                                        let low = self.hex4()?;
                                        if (0xDC00..0xE000).contains(&low) {
                                            let combined = 0x10000
                                                + ((hex - 0xD800) << 10)
                                                + (low - 0xDC00);
                                            char::from_u32(combined).unwrap_or('\u{FFFD}')
                                        } else {
                                            '\u{FFFD}'
                                        }
                                    } else {
                                        return Err(self.err("bad surrogate pair"));
                                    }
                                } else {
                                    return Err(self.err("bad surrogate pair"));
                                }
                            } else {
                                char::from_u32(hex).unwrap_or('\u{FFFD}')
                            };
                            out.push(ch);
                        }
                        _ => return Err(self.err("bad escape")),
                    }
                }
                _ => {
                    // Decode one UTF-8 scalar from the raw source.
                    let start = self.pos - 1;
                    let width = utf8_width(byte);
                    if width == 0 {
                        return Err(self.err("invalid utf-8"));
                    }
                    let end = start + width;
                    if end > self.src.len() {
                        return Err(self.err("truncated utf-8"));
                    }
                    let decoded = std::str::from_utf8(&self.src[start..end])
                        .map_err(|_| self.err("invalid utf-8"))?;
                    if let Some(ch) = decoded.chars().next() {
                        out.push(ch);
                    }
                    self.pos = end;
                }
            }
        }
    }

    fn hex4(&mut self) -> Result<u32, ParseError> {
        if self.pos + 4 > self.src.len() {
            return Err(self.err("truncated \\u escape"));
        }
        let text = std::str::from_utf8(&self.src[self.pos..self.pos + 4])
            .map_err(|_| self.err("bad \\u escape"))?;
        let value = u32::from_str_radix(text, 16).map_err(|_| self.err("bad \\u escape"))?;
        self.pos += 4;
        Ok(value)
    }

    fn number(&mut self) -> Result<f64, ParseError> {
        let start = self.pos;
        while let Some(b) = self.peek() {
            if b.is_ascii_digit() || matches!(b, b'-' | b'+' | b'.' | b'e' | b'E') {
                self.pos += 1;
            } else {
                break;
            }
        }
        let text = std::str::from_utf8(&self.src[start..self.pos])
            .map_err(|_| self.err("bad number"))?;
        text.parse::<f64>().map_err(|_| self.err("bad number"))
    }

    fn value(&mut self) -> Result<Value, ParseError> {
        self.skip_ws();
        match self.peek().ok_or_else(|| self.err("unexpected end"))? {
            b'{' => {
                self.pos += 1;
                let mut entries = Vec::new();
                self.skip_ws();
                if self.peek() == Some(b'}') {
                    self.pos += 1;
                    return Ok(Value::Obj(entries));
                }
                loop {
                    self.skip_ws();
                    let key = self.string()?;
                    self.expect(b':')?;
                    let val = self.value()?;
                    entries.push((key, val));
                    self.skip_ws();
                    match self.peek().ok_or_else(|| self.err("unterminated object"))? {
                        b',' => self.pos += 1,
                        b'}' => {
                            self.pos += 1;
                            return Ok(Value::Obj(entries));
                        }
                        _ => return Err(self.err("expected ',' or '}'")),
                    }
                }
            }
            b'[' => {
                self.pos += 1;
                let mut items = Vec::new();
                self.skip_ws();
                if self.peek() == Some(b']') {
                    self.pos += 1;
                    return Ok(Value::Arr(items));
                }
                loop {
                    let val = self.value()?;
                    items.push(val);
                    self.skip_ws();
                    match self.peek().ok_or_else(|| self.err("unterminated array"))? {
                        b',' => self.pos += 1,
                        b']' => {
                            self.pos += 1;
                            return Ok(Value::Arr(items));
                        }
                        _ => return Err(self.err("expected ',' or ']'")),
                    }
                }
            }
            b'"' => Ok(Value::Str(self.string()?)),
            b't' => self.literal("true", Value::Bool(true)),
            b'f' => self.literal("false", Value::Bool(false)),
            b'n' => self.literal("null", Value::Null),
            _ => Ok(Value::Num(self.number()?)),
        }
    }
}

fn utf8_width(byte: u8) -> usize {
    match byte {
        0x00..=0x7F => 1,
        0xC2..=0xDF => 2,
        0xE0..=0xEF => 3,
        0xF0..=0xF4 => 4,
        _ => 0,
    }
}

pub fn parse_json(src: &str) -> Result<Value, ParseError> {
    let mut parser = Parser::new(src);
    let value = parser.value()?;
    parser.skip_ws();
    if parser.pos != parser.src.len() {
        return Err(parser.err("trailing characters"));
    }
    Ok(value)
}

// ---------------------------------------------------------------------------
// Manifest model <-> JSON
// ---------------------------------------------------------------------------

pub fn parse_manifest(src: &str) -> Result<Manifest, ParseError> {
    let value = parse_json(src)?;
    let mut manifest = Manifest { version: MANIFEST_VERSION, ..Default::default() };
    match &value {
        Value::Obj(_) => {}
        _ => return Err(ParseError::new("manifest must be a JSON object", 0)),
    }
    if let Some(Value::Num(v)) = value.get("version") {
        manifest.version = *v as i64;
    }
    if let Some(Value::Num(v)) = value.get("updated_at") {
        manifest.updated_at = *v as i64;
    }
    let modules = match value.get("modules") {
        Some(Value::Arr(items)) => items.clone(),
        Some(_) => return Err(ParseError::new("\"modules\" must be an array", 0)),
        None => return Ok(manifest),
    };
    for item in modules {
        let id = match item.get("id") {
            Some(Value::Str(s)) if !s.is_empty() => s.clone(),
            _ => return Err(ParseError::new("module entry requires non-empty \"id\"", 0)),
        };
        if manifest.modules.iter().any(|m| m.id == id) {
            return Err(ParseError::new(&format!("duplicate module id \"{id}\""), 0));
        }
        let name = match item.get("name") {
            Some(Value::Str(s)) => s.clone(),
            _ => id.clone(),
        };
        let version = match item.get("version") {
            Some(Value::Str(s)) => s.clone(),
            _ => String::new(),
        };
        let version_code = match item.get("version_code") {
            Some(Value::Num(v)) => *v as i64,
            _ => -1,
        };
        let enabled = match item.get("enabled") {
            Some(Value::Bool(b)) => *b,
            _ => true,
        };
        manifest.modules.push(Entry { id, name, version, version_code, enabled });
    }
    Ok(manifest)
}

// ---------------------------------------------------------------------------
// C ABI consumed by the Zig + C link manager
// ---------------------------------------------------------------------------

/// Fixed-size entry handed across the C ABI. Buffers are NUL-terminated; overlong strings are
/// rejected on input and truncated to the NUL-padded capacity on output.
#[repr(C)]
pub struct MetroEntry {
    pub id: [u8; 160],
    pub name: [u8; 160],
    pub version: [u8; 96],
    pub version_code: i64,
    pub enabled: i32,
    pub _pad: i32,
}

impl MetroEntry {
    fn from_entry(entry: &Entry) -> MetroEntry {
        let mut out = MetroEntry {
            id: [0; 160],
            name: [0; 160],
            version: [0; 96],
            version_code: entry.version_code,
            enabled: if entry.enabled { 1 } else { 0 },
            _pad: 0,
        };
        copy_into(&mut out.id, &entry.id);
        copy_into(&mut out.name, &entry.name);
        copy_into(&mut out.version, &entry.version);
        out
    }

    fn to_entry(&self) -> Option<Entry> {
        let id = c_string(&self.id)?;
        if id.is_empty() {
            return None;
        }
        Some(Entry {
            id,
            name: c_string(&self.name).unwrap_or_default(),
            version: c_string(&self.version).unwrap_or_default(),
            version_code: self.version_code,
            enabled: self.enabled != 0,
        })
    }
}

fn copy_into(buf: &mut [u8], value: &str) {
    let bytes = value.as_bytes();
    let take = bytes.len().min(buf.len() - 1);
    buf[..take].copy_from_slice(&bytes[..take]);
}

fn c_string(buf: &[u8]) -> Option<String> {
    let end = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
    String::from_utf8(buf[..end].to_vec()).ok()
}

const ERR_OK: i32 = 0;
const ERR_BADARG: i32 = -2;
const ERR_PARSE: i32 = -3;

use std::sync::Mutex;
use std::sync::OnceLock;

static LAST_ERROR: OnceLock<Mutex<String>> = OnceLock::new();

fn last_error() -> &'static Mutex<String> {
    LAST_ERROR.get_or_init(|| Mutex::new(String::new()))
}

fn set_error(message: String) {
    if let Ok(mut guard) = last_error().lock() {
        *guard = message;
    }
}

// Handles are opaque boxed manifests passed as u64: Android heap pointers carry a tag byte
// in the top bits (TBI), which would look negative through a signed integer. 0 is the only
// invalid handle; failure details always go through metroconf_error.

/// Parses `data[..len]` as a manifest. Returns an opaque handle or 0 (see metroconf_error).
#[no_mangle]
pub extern "C" fn metroconf_parse(data: *const u8, len: usize) -> u64 {
    if data.is_null() {
        set_error("null input".into());
        return 0;
    }
    let bytes = unsafe { std::slice::from_raw_parts(data, len) };
    let text = match std::str::from_utf8(bytes) {
        Ok(text) => text,
        Err(_) => {
            set_error("manifest is not valid utf-8".into());
            return 0;
        }
    };
    match parse_manifest(text) {
        Ok(manifest) => Box::into_raw(Box::new(manifest)) as u64,
        Err(err) => {
            set_error(format!("{} at offset {}", err.message, err.offset));
            0
        }
    }
}

/// Fresh empty manifest handle (used to construct a manifest from scratch).
#[no_mangle]
pub extern "C" fn metroconf_new() -> u64 {
    Box::into_raw(Box::new(Manifest::default())) as u64
}

#[no_mangle]
pub extern "C" fn metroconf_close(handle: u64) {
    if handle != 0 {
        unsafe { drop(Box::from_raw(handle as *mut Manifest)) };
    }
}

/// Serializes the manifest into `buf`; returns the full required length (excluding NUL), so the
/// caller can retry with a larger buffer when `cap` is too small.
#[no_mangle]
pub extern "C" fn metroconf_serialize(handle: u64, buf: *mut u8, cap: usize) -> i64 {
    if handle == 0 || (cap > 0 && buf.is_null()) {
        return ERR_BADARG as i64;
    }
    let manifest = unsafe { &*(handle as *const Manifest) };
    let json = manifest.to_json();
    let needed = json.len();
    if cap > needed && !buf.is_null() {
        let out = unsafe { std::slice::from_raw_parts_mut(buf, cap) };
        out[..needed].copy_from_slice(json.as_bytes());
        out[needed] = 0;
    }
    needed as i64
}

#[no_mangle]
pub extern "C" fn metroconf_count(handle: u64) -> i64 {
    if handle == 0 {
        return ERR_BADARG as i64;
    }
    let manifest = unsafe { &*(handle as *const Manifest) };
    manifest.modules.len() as i64
}

#[no_mangle]
pub extern "C" fn metroconf_entry(handle: u64, index: i64, out: *mut MetroEntry) -> i32 {
    if handle == 0 || out.is_null() {
        return ERR_BADARG;
    }
    let manifest = unsafe { &*(handle as *const Manifest) };
    match manifest.modules.get(index as usize) {
        Some(entry) => {
            unsafe { *out = MetroEntry::from_entry(entry) };
            ERR_OK
        }
        None => ERR_BADARG,
    }
}

#[no_mangle]
pub extern "C" fn metroconf_upsert(handle: u64, entry: *const MetroEntry) -> i32 {
    if handle == 0 || entry.is_null() {
        return ERR_BADARG;
    }
    let parsed = match unsafe { &*entry }.to_entry() {
        Some(parsed) => parsed,
        None => return ERR_BADARG,
    };
    let manifest = unsafe { &mut *(handle as *mut Manifest) };
    manifest.upsert(parsed);
    ERR_OK
}

#[no_mangle]
pub extern "C" fn metroconf_remove(handle: u64, id: *const u8) -> i32 {
    if handle == 0 || id.is_null() {
        return ERR_BADARG;
    }
    let id = match unsafe { c_slice(id) } {
        Some(id) => id,
        None => return ERR_BADARG,
    };
    let manifest = unsafe { &mut *(handle as *mut Manifest) };
    if manifest.remove(&id) {
        ERR_OK
    } else {
        ERR_BADARG
    }
}

#[no_mangle]
pub extern "C" fn metroconf_set_enabled(handle: u64, id: *const u8, enabled: i32) -> i32 {
    if handle == 0 || id.is_null() {
        return ERR_BADARG;
    }
    let id = match unsafe { c_slice(id) } {
        Some(id) => id,
        None => return ERR_BADARG,
    };
    let manifest = unsafe { &mut *(handle as *mut Manifest) };
    match manifest.modules.iter_mut().find(|m| m.id == id) {
        Some(entry) => {
            entry.enabled = enabled != 0;
            ERR_OK
        }
        None => ERR_BADARG,
    }
}

#[no_mangle]
pub extern "C" fn metroconf_set_updated(handle: u64, updated_at: i64) -> i32 {
    if handle == 0 {
        return ERR_BADARG;
    }
    let manifest = unsafe { &mut *(handle as *mut Manifest) };
    manifest.updated_at = updated_at;
    ERR_OK
}

/// Writes the latest error message into `buf`; returns the message length.
#[no_mangle]
pub extern "C" fn metroconf_error(buf: *mut u8, cap: usize) -> i32 {
    if buf.is_null() || cap == 0 {
        return 0;
    }
    let message = last_error().lock().map(|guard| guard.clone()).unwrap_or_default();
    let out = unsafe { std::slice::from_raw_parts_mut(buf, cap) };
    let take = message.len().min(cap - 1);
    out[..take].copy_from_slice(&message.as_bytes()[..take]);
    out[take] = 0;
    take as i32
}

unsafe fn c_slice(ptr: *const u8) -> Option<String> {
    let mut len = 0usize;
    while *ptr.add(len) != 0 {
        len += 1;
    }
    String::from_utf8(std::slice::from_raw_parts(ptr, len).to_vec()).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r#"{
        "version": 1,
        "updated_at": 1756350000,
        "modules": [
            {"id": "abc", "name": "Alpha", "version": "1.2", "version_code": 12, "enabled": true},
            {"id": "def", "enabled": false}
        ]
    }"#;

    #[test]
    fn parse_roundtrip() {
        let manifest = parse_manifest(SAMPLE).unwrap();
        assert_eq!(manifest.modules.len(), 2);
        assert_eq!(manifest.modules[0].name, "Alpha");
        assert_eq!(manifest.modules[0].version_code, 12);
        assert!(manifest.modules[0].enabled);
        assert!(!manifest.modules[1].enabled);
        assert_eq!(manifest.modules[1].version_code, -1);

        let json = manifest.to_json();
        let reparsed = parse_manifest(&json).unwrap();
        assert_eq!(manifest, reparsed);
    }

    #[test]
    fn rejects_duplicates_and_bad_ids() {
        let dup = r#"{"modules":[{"id":"a"},{"id":"a"}]}"#;
        assert!(parse_manifest(dup).is_err());
        let empty = r#"{"modules":[{"id":""}]}"#;
        assert!(parse_manifest(empty).is_err());
    }

    #[test]
    fn handles_escapes() {
        let manifest = parse_manifest(r#"{"modules":[{"id":"a","name":"quote\" slash\\ tab\t"}]}"#).unwrap();
        assert_eq!(manifest.modules[0].name, "quote\" slash\\ tab\t");
    }
}
