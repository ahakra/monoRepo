//! Example with two named inputs and four named outputs, including a dynamic value.
use etl_wasm_sdk::{Result, dynamic_port, inputs, list_port, project, records};
use serde_json::{Value, json};

pub fn manifest() -> Value {
    json!({"abiVersion": 1, "kind": "transformer", "name": "summarize-usage",
        "inputs": [list_port("usage", "mixed"), dynamic_port("batch")],
        "outputs": [list_port("cdr", "cdr"), list_port("sms", "sms"),
            list_port("rejected", "mixed"), dynamic_port("summary")]})
}

pub fn process(input: &[u8]) -> Result<Value> {
    let input = inputs(input, &["usage", "batch"])?;
    let batch = input["batch"]
        .as_str()
        .filter(|s| !s.is_empty())
        .ok_or("batch must be a nonempty string")?;
    let mut cdr = Vec::new();
    let mut sms = Vec::new();
    let mut rejected = Vec::new();
    for record in records(&input["usage"])? {
        match record.get("record_type").and_then(Value::as_str) {
            Some("CDR") => cdr.push(project(record, "cdr")?),
            Some("SMS") => sms.push(project(record, "sms")?),
            _ => rejected.push(project(record, "mixed")?),
        }
    }
    let summary = json!({"batch": batch, "cdrCount": cdr.len(), "smsCount": sms.len(), "rejectedCount": rejected.len()});
    Ok(json!({"cdr": cdr, "sms": sms, "rejected": rejected, "summary": summary}))
}

etl_wasm_sdk::export_module!(process, manifest);
