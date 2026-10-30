# Java Apache Camel dynamic engine

The initial engine runs saved **File** workflows through
`ApacheCamelDynamicEngine`. Saving a draft never starts execution. Start/stop is
explicit through the backend API; the frontend does not yet have engine controls.
No workflows are automatically restarted when the application restarts.

## Lifecycle API

For the seeded telecom workflow:

```sh
# Validate saved references, schemas, WASM manifests, and show fused stages.
curl --fail http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine/plan

# Starts a real File consumer: input files are read, outputs written, and inputs moved.
curl --fail -X POST http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine/start

# In-memory status, successful/failed file counters, and last error.
curl --fail http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine

# Drain the source exchange, stop downstream consumers, and remove generated routes.
curl --fail -X POST http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine/stop
```

For a trial, point the source File connector at a scratch directory and copy
`examples/telecom/input/usage.csv` there. Using the seeded path directly consumes
that tracked sample and moves it into `input/.processed` on success. Output paths
are configured by the saved destination connectors. Existing output filenames
fail rather than overwrite; choose distinct filenames/directories or move prior
outputs before another run. Empty record lists do not create files.

Startup compiles the graph and resolves all catalog references and WASM binaries
before installing routes. Incomplete/disconnected drafts, unsupported blocks,
missing inputs, stale port snapshots, mismatched schemas/manifests, or unavailable
modules produce HTTP 400. Unknown workflows return 404. Duplicate starts or two
active workflows consuming the same normalized source-directory path return 409.
Deleting an active workflow also returns 409; stop it first. Saving edits is
allowed, but running routes retain their startup snapshot until stop/start.

## Execution model

The compiler topologically orders the DAG, then installs synchronous Camel
`direct:` routes for executable stages. The source route uses the saved File
connector in `from(...)`, calls intermediate routes with `.to(...)`, then calls
terminal routes whose final `.to(...)` is a real File producer. Runtime route IDs
start with `etl-<workflow-id>-`.

Consecutive normalization nodes fuse only across a single outgoing edge into a
normalization node with one incoming edge. A branch or merge ends that chain.
The telecom example's twelve rules become one stage. That stage traverses the
record list once, creates one working record, and applies every rule in order
without copying the map per rule. The existing preview API still copies records
and retains its previous behavior.

Each source file owns an execution state on its Camel exchange. Named output
handles feed named target inputs. Fan-out copies values so one branch cannot
mutate another. Multiple producers may concatenate into a Fields-list input in
plan order; raw bytes and dynamic values require exactly one producer. Conditional
rules split a list into `matched` and `not-matched` arrays, including named empty
arrays. A transformer runs after all upstream stages have supplied its named
inputs **within that same file exchange**. There is no join across separate files
or exchanges.

WASM modules are parsed at compilation, while each invocation gets a fresh
Chicory instance. The host checks the ABI and module kind, exact port names/kinds,
and Fields dictionaries. It implements the shared pointer/length allocation ABI,
validates output record shapes/types, and uses `BigDecimal` when decoding decimal
JSON values. UUID/date/datetime values are checked against their declared types;
null remains allowed so business rules can route incomplete records to rejection.
Raw File source bytes feed mapper inputs without a JSON/base64 wrapper.

All computational stages finish before destination writes begin. Fields lists
are serialized as CSV with stable alphabetic headers, escaping, and the saved
output charset (UTF-8 by default). Dynamic values serialize as JSON. Direct raw
source-to-destination paths copy bytes. A terminal destination accepts one arrow.
Output File producers apply `directoryName`, `fileName`, `charset`, and `autoCreate`;
consumer polling/move/read-lock settings are used only on the source.

The File consumer's saved `move`, `moveFailed`, read lock and polling settings
remain active. A WASM error/trap, validation error, or destination I/O failure
fails the Camel exchange with no automatic redelivery. Business rejection through
a conditional branch is a successful exchange if its destination write succeeds.

## Bounds and current limits

```properties
etl.engine.max-bytes=67108864
etl.engine.wasm-max-pages=4096
etl.engine.wasm-instruction-limit=100000000
```

The host reads at most the configured source size plus one byte before rejecting
an oversized file. The same byte limit bounds WASM files, requests, manifests,
and result buffers; the shared ABI limits inputs to 64 MiB. Each memory page is
64 KiB; 4096 pages cap one WASM instance at 256 MiB. Instruction budgets include
initialization/export calls and are enforced by Chicory's interpreter listener.
Modules must be import-free and have no start section. A trap discards its
instance and fails the exchange.

This version processes whole bounded files/lists in memory; it is not a streaming
CSV parser. Multi-destination filesystem writes are not transactional: a later
I/O failure can leave an earlier destination written. File moves and counters do
not provide an exactly-once guarantee. Status/counters are in memory, not a
persisted execution history. PostgreSQL execution, placeholder steps, cross-file
joins, scheduling, resumable runs, and engine UI controls remain future work.
The existing illustrative hello-world route is independent of this engine.

The engine uses Chicory's [memory interface](https://chicory.dev/docs/usage/memory/)
and Camel's [File component](https://camel.apache.org/components/4.22.x/file-component.html).
Run `./mvnw test` from this service directory for integration tests using the real
compiled WASM artifacts and isolated temporary input/output directories. Rebuild
Rust binaries from the repository root with `make wasm-build` after changing Rust.
