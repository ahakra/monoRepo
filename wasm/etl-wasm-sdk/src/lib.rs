//! Runtime-neutral ETL ABI v1. All memory is owned by the module until freed by the host.
use serde_json::{Map, Value, json};

pub type Result<T> = std::result::Result<T, String>;
pub const ABI_VERSION: u32 = 1;
pub const MAX_INPUT_BYTES: usize = 64 * 1024 * 1024;

pub const TELECOM_FIELDS: &[(&str, &str)] = &[
    ("id", "uuid"),
    ("datetime", "datetime"),
    ("record_id", "string"),
    ("record_type", "string"),
    ("calling", "string"),
    ("called", "string"),
    ("normalizedcalling", "string"),
    ("normalizedcalled", "string"),
    ("start_time", "datetime"),
    ("end_time", "datetime"),
    ("duration_seconds", "integer"),
    ("sms_count", "integer"),
    ("direction", "string"),
    ("status", "string"),
    ("charge_amount", "decimal"),
    ("currency", "string"),
    ("imsi", "string"),
    ("imei", "string"),
    ("cell_id", "string"),
    ("operator_code", "string"),
];

pub fn fields(kind: &str) -> Map<String, Value> {
    TELECOM_FIELDS
        .iter()
        .filter(|(key, _)| match kind {
            "cdr" => !["record_type", "sms_count"].contains(key),
            "sms" => !["record_type", "end_time", "duration_seconds"].contains(key),
            _ => true,
        })
        .map(|(key, ty)| ((*key).into(), json!(ty)))
        .collect()
}

pub fn list_port(name: &str, schema: &str) -> Value {
    json!({"name": name, "kind": "FIELD_LIST", "fields": fields(schema)})
}

pub fn dynamic_port(name: &str) -> Value {
    json!({"name": name, "kind": "DYNAMIC_VARIABLE"})
}

/// Transformer input names must match exactly, including capitalization.
pub fn inputs(bytes: &[u8], names: &[&str]) -> Result<Map<String, Value>> {
    let request: Value =
        serde_json::from_slice(bytes).map_err(|e| format!("Invalid input JSON: {e}"))?;
    let envelope = request.as_object().ok_or("Expected an input object")?;
    if envelope.len() != 2 || envelope.get("abiVersion") != Some(&json!(ABI_VERSION)) {
        return Err("Expected only abiVersion: 1 and inputs".into());
    }
    let inputs = envelope
        .get("inputs")
        .and_then(Value::as_object)
        .ok_or("Expected named inputs")?;
    if inputs.len() != names.len() || names.iter().any(|name| !inputs.contains_key(*name)) {
        return Err(format!(
            "Expected exactly these input ports: {}",
            names.join(", ")
        ));
    }
    Ok(inputs.clone())
}

pub fn records(value: &Value) -> Result<&Vec<Value>> {
    let records = value.as_array().ok_or("Expected a record array")?;
    if records.iter().any(|record| !record.is_object()) {
        return Err("Every record must be an object".into());
    }
    Ok(records)
}

pub fn project(record: &Value, kind: &str) -> Result<Value> {
    let source = record.as_object().ok_or("Expected a record object")?;
    let mut result = Map::new();
    for key in fields(kind).keys() {
        let value = source
            .get(key)
            .ok_or_else(|| format!("Missing field '{key}'"))?;
        result.insert(key.clone(), value.clone());
    }
    Ok(Value::Object(result))
}

pub fn projection(bytes: &[u8], kind: &str) -> Result<Value> {
    let inputs = inputs(bytes, &["usage"])?;
    let output: Vec<Value> = records(&inputs["usage"])?
        .iter()
        .map(|record| project(record, kind))
        .collect::<Result<_>>()?;
    Ok(json!({kind: output}))
}

pub fn projection_manifest(kind: &str) -> Value {
    json!({"abiVersion": ABI_VERSION, "kind": "transformer", "name": format!("project-{kind}"),
        "inputs": [list_port("usage", "mixed")], "outputs": [list_port(kind, kind)]})
}

// Use boxed slices rather than Vec capacities so the host frees exactly (pointer, length).
// A zero allocation is rejected; valid output JSON always has a nonzero length.
pub fn allocate(len: u32) -> u32 {
    if len == 0 || len as usize > MAX_INPUT_BYTES {
        return 0;
    }
    Box::into_raw(vec![0u8; len as usize].into_boxed_slice()) as *mut u8 as u32
}

/// # Safety
/// ptr/len must describe a live allocation returned by this module, freed exactly once.
pub unsafe fn release(ptr: u32, len: u32) {
    if ptr != 0 && len != 0 {
        unsafe {
            drop(Box::from_raw(std::ptr::slice_from_raw_parts_mut(
                ptr as *mut u8,
                len as usize,
            )));
        }
    }
}

pub fn return_json(value: Value) -> u64 {
    let bytes = serde_json::to_vec(&value)
        .expect("JSON Value serialization")
        .into_boxed_slice();
    let len = u32::try_from(bytes.len()).expect("WASM32 result length");
    let ptr = Box::into_raw(bytes) as *mut u8 as u32;
    ((ptr as u64) << 32) | len as u64
}

/// # Safety
/// ptr/len must be an initialized input allocation from etl_alloc, still owned by the host.
pub unsafe fn execute(ptr: u32, len: u32, handler: fn(&[u8]) -> Result<Value>) -> u64 {
    if ptr == 0 || len == 0 || len as usize > MAX_INPUT_BYTES {
        return return_json(json!({"abiVersion": ABI_VERSION,
            "error": {"code": "INVALID_INPUT", "message": "Input must contain 1 to 67108864 bytes"}}));
    }
    let input = unsafe { std::slice::from_raw_parts(ptr as *const u8, len as usize) };
    let response = match handler(input) {
        Ok(outputs) => json!({"abiVersion": ABI_VERSION, "outputs": outputs}),
        Err(message) => json!({"abiVersion": ABI_VERSION,
            "error": {"code": "INVALID_INPUT", "message": message}}),
    };
    return_json(response)
}

/// Export one shared ABI from every module; handlers contain business logic only.
#[macro_export]
macro_rules! export_module {
    ($handler:path, $manifest:path) => {
        #[unsafe(no_mangle)]
        pub extern "C" fn etl_abi_version() -> u32 {
            $crate::ABI_VERSION
        }
        #[unsafe(no_mangle)]
        pub extern "C" fn etl_alloc(len: u32) -> u32 {
            $crate::allocate(len)
        }
        /// # Safety
        /// The host must pass an outstanding allocation from this module exactly once.
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn etl_free(ptr: u32, len: u32) {
            unsafe { $crate::release(ptr, len) }
        }
        /// # Safety
        /// Input must be a live etl_alloc allocation initialized with len input bytes.
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn etl_process(ptr: u32, len: u32) -> u64 {
            unsafe { $crate::execute(ptr, len, $handler) }
        }
        #[unsafe(no_mangle)]
        pub extern "C" fn etl_manifest() -> u64 {
            $crate::return_json($manifest())
        }
    };
}
