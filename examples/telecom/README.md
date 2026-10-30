# Telecom file workflow demo

Restart the backend (`make backend`) to run `TelecomDemoSeeder`, then refresh the
frontend and open **Workflows → Telecom - CSV to CDR and SMS**. The Java startup seeder
creates the definitions in one transaction, uses stable IDs, and preserves existing
user data. It skips seeding when the demo workflow already exists, including edits
to that workflow. All demo entries start with `Telecom -`.

The saved workflow can now run through the Java Camel engine. Saving alone keeps it
inactive. Use **Run** (or **Save and run**) in the editor, then **Stop** to end
processing. Status and file counters update automatically. The [engine lifecycle API](../../services/camel-integration/ENGINE.md)
also lets you inspect the compiled plan, start consumption, view counters, and stop. A start
reads real files and moves the input to `.processed` or `.invalid`; use a scratch
source directory for trials if you want to keep the tracked sample in place.

Rust source and compiled modules are provided for the CSV mapper and CDR/SMS
projections. Run `make wasm-build` to rebuild them; see the
[WASM contract and build guide](../../wasm/README.md).

The graph follows this sequence:

1. Read `input/usage.csv` through the File connector and parse CSV into the
   **Telecom - mixed usage** Fields list, named `usage` on the mapper output.
2. Apply the six numbered calling rules, then the six numbered called rules.
3. Match CDR records; send matches through the CDR projection transformer.
4. Test remaining records against the SMS rule; send matches through the SMS
   projection transformer. Send remaining records to the rejected file destination.
5. Write projected records to separate CDR/SMS file destinations through the final Camel File producers.

Three field dictionaries cover mixed input, CDR output, and SMS output. They
include record identifiers, timestamps, original and normalized calling/called
numbers, direction/status, charges/currency, IMSI, IMEI, cell and operator codes.
The CDR schema omits `record_type` and `sms_count`; the SMS schema omits
`record_type`, `end_time`, and `duration_seconds`.

The CSV header defines the raw input contract. Empty cells represent nulls;
numeric fields must be cast to their schema types. The mapper contract generates
a UUID `id`, preserves the file's `datetime`, and initializes `normalizedcalling`
and `normalizedcalled` to null. The projection contracts retain only their output
schema's fields without changing values.

Normalization preserves `calling` and `called`, writing to `normalizedcalling`
and `normalizedcalled`. In order: trim into the normalized field, remove spaces,
remove hyphens, remove an initial `00`, prefix eight-digit local mobile numbers
with `+961`, then prefix numbers starting with `961` with `+`.
For example, ` 00961 71 123456 `, `961-71-123456`, `71123456`, and
`+96171123456` all become `+96171123456`.

This deliberately small Lebanon demo supports eight-digit local mobile numbers
without a trunk zero and the international forms above. It does not implement
full numbering-plan validation, landlines, extensions, or arbitrary countries.
The matching rules require an exact uppercase `CDR` or `SMS` record type, both
normalized numbers starting with `+961`, nonnegative charges, and either a
nonnegative call duration or a positive SMS count. Zero-duration missed calls
are accepted. Null/invalid values in those required fields fail matching.

Expected routing for the included synthetic sample:

| Record | Destination | normalizedcalling | normalizedcalled |
| --- | --- | --- | --- |
| CALL-001 | CDR | +96171123456 | +96176234567 |
| CALL-002 | CDR | +96171123456 | +96176234567 |
| SMS-001 | SMS | +96171123456 | +96176234567 |
| SMS-002 | SMS | +96171123456 | +96176234567 |
| BAD-001 | Rejected (unknown type) | +96171123456 | +96176234567 |
| BAD-002 | Rejected (negative duration) | +96171123456 | +96176234567 |
| BAD-003 | Rejected (missing caller) | null | +96176234567 |

Paths are relative to the Java service working directory used by `make backend`:
`../../examples/telecom/input`, `../../examples/telecom/output/cdr`,
`../../examples/telecom/output/sms`, and `../../examples/telecom/output/rejected`.
If you launch the backend from another directory, edit these connectors to use
absolute paths. The destinations derive their names from the input: `usage.csv` produces
`usage-cdr.csv`, `usage-sms.csv`, and `usage-rejected.csv`. For example,
`usage copy 3.csv` produces `usage copy 3-cdr.csv` and the corresponding SMS and
rejected files.
The input connector uses UTF-8, a changed-file read lock, and `.processed` /
`.invalid` move directories. No physical database tables or output files are
created by the seeder.

The Java source is
[`TelecomDemoSeeder.java`](../../services/camel-integration/src/main/java/com/ahvakra/camelintegration/seed/TelecomDemoSeeder.java).
It builds domain objects through the existing stores, validates File settings,
validates conditional field references, and validates the workflow graph before
saving. Set `app.seed.telecom-demo.enabled=false` to disable the Java startup
seeder. Historical migration V14 is retained unchanged because it has already
been applied; its original one-time seed remains part of migration history.
Disabling the Java seeder does not undo or disable that historical migration.

The editor's **Activity** panel below the canvas shows step/rule events, branch
counts, durations, failures and actual output paths. **Show step** focuses the
corresponding block. The current file/step appears above the canvas; fast steps
remain available in the history. Server traces are also written to
`services/camel-integration/logs/workflows.log` when launched with `make backend`.

For another trial, stop the workflow, move/delete the old output CSVs, move the
consumed file from `input/.processed/usage.csv` back into `input/usage.csv`, and
run again. Fix failed inputs before moving them back from `.invalid`. No files
are deleted or requeued by the UI. `fixtures/usage.csv` is the canonical sample
used by the tests and is outside the consumed directory.

Migration V15 replaces only the demo's original fixed destination filenames with
`${file:onlyname.noext.single}-cdr.csv`, `${file:onlyname.noext.single}-sms.csv`,
and `${file:onlyname.noext.single}-rejected.csv`. Customized names remain intact.
Stop and Run after applying the migration to load the changed connectors. Leaving
a destination's filename blank preserves the exact input filename in its own
output directory. Reprocessing the same filename still requires moving/removing
its previous outputs or choosing a unique filename expression; files are not
overwritten automatically.
