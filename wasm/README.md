# Rust ETL WASM modules

This workspace implements the mapper/transformer boundary before the Camel engine.
It builds real modules at the locations already saved in the telecom demo.
The Java backend does not load or execute these modules yet.

```sh
rustup target add wasm32-unknown-unknown
make wasm-build   # from repository root
make wasm-test    # Rust unit tests, build/package, then compiled WASM tests in Node
```

Builds use the committed `Cargo.lock`. Node is needed only to package/test; the
modules themselves have no imports or JavaScript dependencies. The Rust
[`wasm32-unknown-unknown` target](https://doc.rust-lang.org/rustc/platform-support/wasm32-unknown-unknown.html)
supports this import-free boundary. Generated release modules are copied into
`examples/telecom/wasm/` with the hyphenated names configured by the Java seeder.
Rebuild those artifacts after changing Rust source.

| File | Input | Named outputs |
| --- | --- | --- |
| `parse-usage.wasm` | Raw UTF-8 CSV bytes | `usage`: mixed usage records |
| `project-cdr.wasm` | `usage`: mixed usage list | `cdr`: CDR records |
| `project-sms.wasm` | `usage`: mixed usage list | `sms`: SMS records |
| `summarize-usage.wasm` | `usage`: mixed usage list; `batch`: dynamic string | `cdr`, `sms`, `rejected`: lists; `summary`: dynamic object |

The first three match the existing saved demo. `summarize-usage` is a separate
multi-port example, not automatically inserted into that workflow. Its example
business logic partitions by record type only; graph normalization and matching
rules remain responsible for number/usage validation.

The mapper handles CSV quoting, escaped quotes, CRLF, reordered headers, and an
optional UTF-8 BOM. Its header must contain exactly the raw sample's columns,
without duplicates; row lengths and UTF-8 must be valid. Empty cells become JSON
null. Numeric fields become JSON numbers, timestamps must have an RFC3339 offset,
and original phone strings stay intact. `id` is a deterministic UUID v5 of the
parsed source record; the same source record produces the same ID on retries.
Identical duplicate rows share an ID; this mapper does not deduplicate them.
Normalized fields start at null. Malformed CSV/type conversion fails the entire
call with a structured error, without partial output. Business-invalid rows such
as negative durations remain available for the workflow's rejected branch.
Rust preserves decimal precision in JSON; the Java host should decode decimals
as `BigDecimal` and avoid conversion through floating point. The Node reference
host uses ordinary JavaScript JSON numbers and is a contract test host.

## ABI v1

Every module exports the same functions and a linear `memory`:

| Export | WASM signature | Purpose |
| --- | --- | --- |
| `etl_abi_version` | `() -> i32` | Returns `1` |
| `etl_alloc` | `(i32 length) -> i32 pointer` | Zero-initialized input allocation; `0` means invalid length |
| `etl_process` | `(i32 pointer, i32 length) -> i64 buffer` | Runs the module synchronously |
| `etl_manifest` | `() -> i64 buffer` | Returns module kind and declared ports/schemas as JSON |
| `etl_free` | `(i32 pointer, i32 length) -> ()` | Frees an outstanding allocation exactly once |

A returned `i64` packs **pointer in its upper 32 bits**, **length in its lower
32 bits**. Treat both as unsigned. Java can decode with:

```java
long pointer = packed >>> 32;
long length = packed & 0xffff_ffffL;
```

For a mapper, `etl_process` receives the **raw file bytes**, not a JSON byte array.
For a transformer, it receives UTF-8 JSON with every configured named input:

```json
{"abiVersion":1,"inputs":{"usage":[{"id":"...","calling":"..."}],"batch":"batch-001"}}
```

The abbreviated record above illustrates framing; projection modules require all
fields in their declared output schema. `project-cdr` and `project-sms` accept
only the `usage` input. Names are case sensitive. Missing/extra ports and wrong
list shapes are errors. A `FIELD_LIST` value is an array of record objects; a
`DYNAMIC_VARIABLE` value is a JSON value whose business type the module validates.
An empty list must still be supplied as `[]`; absence means a missing input.

Successful response bytes contain a JSON envelope:

```json
{"abiVersion":1,"outputs":{"cdr":[],"sms":[],"rejected":[],"summary":{"batch":"batch-001","cdrCount":0,"smsCount":0,"rejectedCount":0}}}
```

A failure contains `error` and no `outputs`:

```json
{"abiVersion":1,"error":{"code":"INVALID_INPUT","message":"Expected exactly these input ports: usage"}}
```

Manifests are bare JSON objects with `abiVersion`, `kind` (`mapper` or
`transformer`), `name`, and `outputs`. Transformers also declare `inputs`; mapper
`input` declares `BYTES`. Each port declares `name`, `kind` (`FIELD_LIST` or
`DYNAMIC_VARIABLE`), and, for lists, `fields` as a key/type dictionary. The engine
must compare these port names/kinds/schemas with the saved definition before
running a module, rather than infer names from array positions.

## Host call lifecycle

1. Instantiate the module, check ABI version, read/validate the manifest, and free
   its returned buffer. Modules need no imported host functions.
2. Allocate input, write exactly that many bytes to module memory, and call
   `etl_process`. Input is limited to 64 MiB per invocation; empty input produces
   a structured error. For large files the engine will need a bounded batch/chunk
   strategy; the CSV module currently maps one complete file per invocation.
3. Decode the packed result, bounds-check it, copy/read the response, and free the
   result and input allocations separately, even after a structured error.
   Never free the same allocation twice or pass pointers from another instance.
4. Reacquire the memory view after a WASM call; memory growth can invalidate prior
   views. Do not invoke one instance concurrently. On a trap, discard the instance
   and fail that exchange; the future host must also impose memory/fuel limits.
5. Require exactly the configured output names and validate list schemas/types
   before passing values to downstream blocks. Empty outputs remain named arrays.

[`host.mjs`](host.mjs) demonstrates allocation, packed-result decoding, response
validation, and cleanup against the actual compiled modules. It is a reference
for the future Java adapter, not the Camel engine.

The future engine can use `from(...)` for the source, `to(...)` for intermediate
processors and the final destination, while passing these named values between
blocks. Consecutive normalization rules should be compiled into an ordered
per-record chain. WASM calls remain explicit batch boundaries; a transformer
may need multiple named inputs before it can run, so those inputs cannot be
silently treated as unrelated `to(...)` calls.
