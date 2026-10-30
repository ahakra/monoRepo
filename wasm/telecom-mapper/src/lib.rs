use chrono::DateTime;
use etl_wasm_sdk::{Result, TELECOM_FIELDS, list_port};
use serde_json::{Map, Value, json};
use std::collections::HashSet;
use uuid::Uuid;

// Stable IDs make retries of the same record deterministic; no host randomness is needed.
const NAMESPACE: Uuid = Uuid::from_u128(0xce1e0000000040008000000000000020);

pub fn manifest() -> Value {
    json!({"abiVersion": 1, "kind": "mapper", "name": "parse-usage",
        "input": {"kind": "BYTES", "format": "text/csv", "encoding": "UTF-8"},
        "outputs": [list_port("usage", "mixed")]})
}

pub fn process(input: &[u8]) -> Result<Value> {
    let text = std::str::from_utf8(input).map_err(|e| format!("CSV must be UTF-8: {e}"))?;
    let mut reader =
        csv::ReaderBuilder::new().from_reader(text.trim_start_matches('\u{feff}').as_bytes());
    let headers = reader
        .headers()
        .map_err(|e| format!("Invalid CSV header: {e}"))?
        .clone();
    let required: Vec<_> = TELECOM_FIELDS
        .iter()
        .filter(|(key, _)| !["id", "normalizedcalling", "normalizedcalled"].contains(key))
        .collect();
    let unique: HashSet<_> = headers.iter().collect();
    if unique.len() != headers.len() {
        return Err("Duplicate CSV header".into());
    }
    if headers.len() != required.len() || required.iter().any(|(key, _)| !unique.contains(key)) {
        return Err(format!(
            "CSV headers must match: {}",
            required
                .iter()
                .map(|(key, _)| *key)
                .collect::<Vec<_>>()
                .join(",")
        ));
    }
    let mut output = Vec::new();
    for (index, row) in reader.records().enumerate() {
        let row = row.map_err(|e| format!("CSV row {}: {e}", index + 2))?;
        let mut record = Map::new();
        for (key, ty) in &required {
            let position = headers
                .iter()
                .position(|header| header == *key)
                .expect("validated header");
            let raw = &row[position];
            let value = parse_value(raw, ty)
                .map_err(|e| format!("CSV row {}, field '{key}': {e}", index + 2))?;
            record.insert((*key).into(), value);
        }
        for key in ["record_id", "datetime"] {
            if record[key].is_null() {
                return Err(format!(
                    "CSV row {}: '{key}' is required for record identity",
                    index + 2
                ));
            }
        }
        // Serialize the ordered fields before adding id; UUID v5 includes every source value.
        let identity = serde_json::to_vec(&record).map_err(|e| e.to_string())?;
        record.insert(
            "id".into(),
            json!(Uuid::new_v5(&NAMESPACE, &identity).to_string()),
        );
        record.insert("normalizedcalling".into(), Value::Null);
        record.insert("normalizedcalled".into(), Value::Null);
        output.push(Value::Object(record));
    }
    Ok(json!({"usage": output}))
}

fn parse_value(raw: &str, ty: &str) -> Result<Value> {
    if raw.is_empty() {
        return Ok(Value::Null);
    }
    match ty {
        "integer" => raw
            .parse::<i32>()
            .map(|n| json!(n))
            .map_err(|_| "Expected a 32-bit integer".into()),
        "decimal" => {
            let value: Value =
                serde_json::from_str(raw).map_err(|_| "Expected a decimal number")?;
            if !value.is_number() {
                return Err("Expected a decimal number".into());
            }
            Ok(value)
        }
        "datetime" => {
            DateTime::parse_from_rfc3339(raw)
                .map_err(|_| "Expected an RFC3339 datetime with a timezone offset")?;
            Ok(json!(raw))
        }
        _ => Ok(json!(raw)),
    }
}

etl_wasm_sdk::export_module!(process, manifest);

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_sample_with_typed_values_and_stable_ids() {
        let bytes = include_bytes!("../../../examples/telecom/fixtures/usage.csv");
        let first = process(bytes).unwrap();
        assert_eq!(first, process(bytes).unwrap());
        let rows = first["usage"].as_array().unwrap();
        assert_eq!(rows.len(), 7);
        assert_eq!(rows[0]["duration_seconds"], 125);
        assert!(rows[0]["charge_amount"].is_number());
        assert!(rows[0]["sms_count"].is_null());
        assert!(rows[0]["normalizedcalling"].is_null());
        assert_eq!(rows[0]["calling"], " 00961 71 123456 ");
        assert!(Uuid::parse_str(rows[0]["id"].as_str().unwrap()).is_ok());
        assert_ne!(rows[0]["id"], rows[1]["id"]);
    }

    #[test]
    fn preserves_decimal_precision_and_accepts_quoted_csv_fields() {
        let sample =
            std::str::from_utf8(include_bytes!("../../../examples/telecom/fixtures/usage.csv"))
                .unwrap();
        let sample = sample
            .replacen(",0.25,", ",0.12345678901234567890123456789,", 1)
            .replacen("CALL-001", "\"CALL,001\"", 1);
        let mapped = process(sample.as_bytes()).unwrap();
        assert_eq!(mapped["usage"][0]["record_id"], "CALL,001");
        assert_eq!(
            mapped["usage"][0]["charge_amount"].to_string(),
            "0.12345678901234567890123456789"
        );
    }

    #[test]
    fn rejects_bad_headers_encoding_and_types_without_partial_output() {
        let bytes = include_bytes!("../../../examples/telecom/fixtures/usage.csv");
        let sample = std::str::from_utf8(bytes).unwrap();
        for invalid in [
            sample.replacen("datetime,record_id", "datetime,datetime", 1),
            sample.replacen(",125,", ",abc,", 1),
            sample.replacen("2026-10-30T08:00:00Z", "no-timezone", 1),
            sample.replacen(",0.25,", ",NaN,", 1),
            sample.replacen("CALL-001", "", 1),
        ] {
            assert!(process(invalid.as_bytes()).is_err());
        }
        assert!(process(&[0xff]).is_err());
    }
}
