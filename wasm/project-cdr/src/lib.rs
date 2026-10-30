use serde_json::Value;

pub fn process(input: &[u8]) -> etl_wasm_sdk::Result<Value> {
    etl_wasm_sdk::projection(input, "cdr")
}

pub fn manifest() -> Value {
    etl_wasm_sdk::projection_manifest("cdr")
}

etl_wasm_sdk::export_module!(process, manifest);
