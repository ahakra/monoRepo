# Java Apache Camel dynamic engine

The initial engine runs saved **File** workflows through
`ApacheCamelDynamicEngine`. Saving a draft never starts execution. Start/stop is
explicit through the backend API or the workflow editor’s Run/Stop controls.
Run becomes Save and run for new or edited drafts, saving successfully before
starting. Cancel closes the editor with an unsaved-change prompt; it does not
stop an active workflow. The editor polls status and shows file counters/errors.
No workflows are automatically restarted when the application restarts.

## Lifecycle API

For the seeded telecom workflow:

```sh
# Validate saved references, schemas, WASM manifests, and show fused stages.
curl --fail http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine/plan

# Starts a real File consumer: input files are read, outputs written, and inputs moved.
curl --fail -X POST http://localhost:8081/api/workflows/ce1e0000-0000-4000-8000-000000000070/engine/start

# Live status with persisted latest-run counters and last error.
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
not provide an exactly-once guarantee. Execution history and per-run counters are
persisted in PostgreSQL. SQL source execution, placeholder steps, cross-file
joins, scheduling and resumable runs remain future work.
The existing illustrative hello-world route is independent of this engine.

The engine uses Chicory's [memory interface](https://chicory.dev/docs/usage/memory/)
and Camel's [File component](https://camel.apache.org/components/4.22.x/file-component.html).
Run `./mvnw test` from this service directory for integration tests using the real
compiled WASM artifacts and isolated temporary input/output directories. Rebuild
Rust binaries from the repository root with `make wasm-build` after changing Rust.

## Workflow tracing and retrying a file

Open **Activity** in the editor header, or scroll below the canvas. The panel
polls this workflow's events, filters by run/level/text, displays actual output
paths with record/byte counts, exports the visible events as JSON, and provides
**Show step** links. The canvas outlines completed, active, skipped and failed
nodes for the latest file. Status reports `phase`, `currentFile`, `currentStep`
and `currentNodeIds`; fast steps remain visible in the event history.

`GET /api/workflows/{id}/engine/events?after=0&limit=2000` returns an ascending
sequence of events plus `nextAfter`, session ID, oldest/newest sequence numbers,
and database-reset indicators. An initial request returns the most recent page;
incremental requests return subsequent events in ascending order. Send `nextAfter` as the next cursor to fetch only
new events. Events have workflow/run/exchange IDs, labels/node IDs, input/output
counts, durations, rule-match counts and output paths. A fused normalization stage
counts rule matches inside its existing record loop; tracing adds no extra pass.
An empty output branch emits `STEP_SKIPPED` rather than a fictitious file write.

The UI keeps at most 2000 events for live updates. Full history is stored in
PostgreSQL by migration V16: `etl_execution_runs` holds run snapshots and counters,
`etl_executions` holds batch descriptions/timestamps/status, `etl_execution_stages`
holds stage results, and `etl_execution_events` holds detailed structured logs.
Each event and its summaries commit together in an independent transaction.
There is no automatic retention deletion. New events are persisted from this version
onward; earlier temporary history is not imported.

`GET /api/workflows/{id}/engine/batches?limit=50&before=<cursor>` returns batch
summaries, newest first, with `nextBefore` and `hasMore`. Omit `before` for the first
page. `GET /api/workflows/{id}/engine/batches/{executionId}` returns the saved batch,
stage snapshots and full batch logs. The shared activity grid uses these APIs;
**Load older batches** reads the next database page. Filters apply to loaded batches.
The editor loads historical stage snapshots independently of live events.

History survives refresh and restart. A new backend marks unfinished batches/runs
from the previous process as interrupted, preserving completed results. Workflows
are not resumed automatically. This ownership model assumes a single backend
instance, like the local route engine itself; it does not coordinate multiple replicas.
When no local route is installed, status includes the latest persisted run/counters.
Error stack traces also appear in the console and dedicated rolling file:

```text
services/camel-integration/logs/workflows.log
```

The path is relative to the backend working directory and can be changed through
`etl.engine.logs-directory`. Files rotate daily or at 10 MB, with up to 14 days
and 200 MB total. Each event is JSON; filter on `workflowId`, `runId` or
`exchangeId`. Runtime tracing records metadata/counts, not source record bodies.
Output events describe a completed write, not a live filesystem inventory: a
file can subsequently be moved or removed outside the application.

To run the same demo file again: **Stop**, move or delete the old output CSVs,
move `input/.processed/usage.csv` back to `input/usage.csv`, then **Run**. For failed
files, correct the input before moving it back from `.invalid`. The UI never
clears outputs or requeues inputs automatically. Startup snapshots reset the
file counters while the retained activity still distinguishes previous runs.

The canonical sample lives at `examples/telecom/fixtures/usage.csv`, outside the
watched input directory. Java, Rust and compiled-WASM tests use that fixture so
consuming/moving the live demo input does not break subsequent builds.

## Stage results in both UI views

The workflow editor and **Executions** page share the same trace data. Each file
execution has a stage table showing names, block types, successful/running/failed/
skipped/unreached outcomes, counts, durations, messages and output paths. The
editor also shows result badges on blocks and execution details for the selected
block. The Executions page selects the workflow, run and input-file execution;
its stage links reopen the corresponding block and historical file result in the
editor. The editor's Executions button opens monitoring for that workflow.

`PLAN_READY` includes a snapshot of original node names/types. Every stage event
includes `stageType` and its original node labels; results therefore retain their
meaning if the workflow is renamed or edited later. The status includes
`currentExchangeId` to distinguish an active file from an older file with the same
filename. Grouped normalization rules have individual match counts/results but
share a chain duration, explicitly labeled in the UI. Both views load saved
batch results and detailed logs from PostgreSQL after a refresh or backend restart.

## Batch grid

Both activity views now group imported data into batches rather than presenting a
flat file selector and mixed event list. Each row shows batch description, source,
creation time, status and duration. A file's description is its filename. Expand
**Show stages** to view that batch's stage results, output files and detailed logs
directly beneath its row. Only one batch is expanded at a time. Startup/stop logs
are separate because they belong to the workflow session rather than one batch.

Trace events carry `batchDescription`, `batchCreatedAt` and `sourceType` in their
details so all stages retain the batch's original identity and time even if early
events rotate away. The UI can consume a generic description or a
`lastImportedId` value for a future SQL batch; SQL execution remains unimplemented.
Repeated filenames in different runs remain separate batches, keyed by run and
exchange IDs. New batches never replace the batch the user currently has open.

## Which connector settings does Run use?

The browser sends the saved workflow ID to `/engine/start`; it does not send a
copy of the connector form. The compiler rereads saved connector settings from
the database on each start. An active consumer retains that startup snapshot;
saving a connector alone does not hot-reconfigure it. Save the connector, then
Stop and Run to apply changes. A regression test edits a File connector's filter,
restarts the same workflow/connector IDs, verifies the actual Camel endpoint's
new settings, and consumes the newly matching filename.

Status includes `sourceConfiguration` with the loaded File connector ID, name,
settings and saved timestamp, without credentials. Both monitoring views expose
it under **Active source settings**. The editor flags a newer saved connector
when its catalog timestamp is newer than the active snapshot. The `PLAN_READY`
and `WORKFLOW_STARTED` events also include the loaded source settings.

For prefix filtering, leave `fileName` empty and set `include=^usage.*\.csv$`.
`fileName` accepts an exact filename or a File Language filename expression;
placing a regex there makes the consumer wait for a literal filename instead.

## Destination filenames

File destinations evaluate their configured `fileName` expression against the
original source filename. The engine restores the original `CamelFileName` and
`CamelFileNameOnly` headers before every destination, so an earlier output never
becomes the next output's filename. An empty `fileName` preserves the original
input basename. Explicit constants remain supported.

The telecom defaults use `${file:onlyname.noext.single}-cdr.csv`, `-sms.csv` and
`-rejected.csv`. The `single` form removes only the last extension, preserving
other dots. Thus `usage.2026.10.csv` produces `usage.2026.10-cdr.csv`, and spaces
in an input name remain in its output name. V15 upgrades only the original demo
constants and leaves customized connector values intact. The Java seeder uses
the same defaults. Existing outputs are untouched. Restart a running workflow to
load its updated connector settings. Reusing the same input filename can still
collide with its prior outputs; the engine continues to fail instead of overwrite.

Filename expressions follow [Camel File Language](https://camel.apache.org/components/4.22.x/languages/file-language.html).
