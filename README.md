# Camel ETL

An integration and ETL platform built with **Spring Boot + Apache Camel** and a **React + Vite** interface. The current implementation provides reusable PostgreSQL connections, typed field dictionaries, table mappings, and reviewed PostgreSQL table and column operations.

## Features

### Connections

Use **Connectors** to create, edit, test, and delete saved connector configurations. File and PostgreSQL are the implemented connection types.

- Configure the host, port, database, username, password, and SSL mode.
- Reuse saved connections when defining tables.
- Configure optional `currentSchema`, `connectTimeout`, `socketTimeout`, and `sslrootcert` in Advanced PostgreSQL settings. Timeouts use seconds; 0 disables them.
- Test a connection before using it.
- Optionally create a missing database with **Create & test**, when the database user has the required permission.
- Keep the existing password when an edit leaves the password blank. Passwords are excluded from API responses.

Saving a connection stores its configuration. Testing and optional database creation are separate actions. A connection referenced by a table configuration cannot be deleted.

Connectors covers File, FTP/SFTP, SQL, and APIs. File and PostgreSQL connection management are implemented; FTP/SFTP, API connectors, and PostgreSQL pipeline execution remain future work. The Java engine can execute File workflows.

### File connectors

Choose **Connectors → New connection → File** to configure a local File consumer using Apache Camel 4.22 option names:

- **Source directory** → `directoryName`.
- **Processed directory** → `move`, default `.processed`.
- **Invalid / failed directory** → `moveFailed`, default `.invalid`.

Advanced options include filename/extension/regex filters, recursive polling, character encoding, polling intervals, in-progress moves, and read locks. **Test directories** checks directory access without creating, moving, or consuming files. Saving only stores configuration; explicit engine startup begins File consumption. Runtime failures fail the exchange and use `moveFailed`.

Saved connector types cannot be changed; create a new connector to use another type.

### Fields

Use **Schemas & Tables → Fields** to define named, reusable dictionaries of field keys and logical types:

```json
{
  "name": "Customer",
  "fields": {
    "id": "uuid",
    "datetime": "datetime",
    "name": "string",
    "lastname": "string"
  }
}
```

Every definition includes fixed `id: uuid` and `datetime: datetime` entries. Custom keys use lowercase letters, numbers, and underscores, start with a letter or underscore, and contain at most 63 characters. Definitions support up to 100 entries, including defaults.

Supported types are `string`, `integer`, `long`, `decimal`, `boolean`, `date`, `datetime`, `uuid`, and `json`.

A **CDR (Call Detail Record)** sample is seeded once by a database migration. It includes call identifiers, caller/called numbers, start/end times, duration, direction, status, charge amount, and currency, alongside the default fields. It contains a field dictionary rather than sample call records.

Used definitions cannot be deleted. Field keys and types used by table mappings are locked in the editor and protected by the API, which reports the referencing tables. Unused fields can still be edited.

### Tables

Use **Schemas & Tables → Tables** to map fields to a target PostgreSQL table:

1. Select an existing **PostgreSQL** connection and one saved Fields definition.
2. Enter a configuration name, an existing schema (default `public`), and a table name.
3. Add all dictionary fields or select them individually. At least one field is required.
4. Save the mapping, inspect its generated SQL, and explicitly choose **Create PostgreSQL table** to create the target table.

Only fields from the chosen dictionary can be selected. Their types are inherited and validated by the backend. Changing the Fields definition clears the selection; `id` and `datetime` are optional in the table selection.

| Field type | PostgreSQL column type |
| --- | --- |
| `string` | `TEXT` |
| `integer` | `INTEGER` |
| `long` | `BIGINT` |
| `decimal` | `NUMERIC` |
| `boolean` | `BOOLEAN` |
| `date` | `DATE` |
| `datetime` | `TIMESTAMP WITH TIME ZONE` |
| `uuid` | `UUID` |
| `json` | `JSONB` |

New columns are nullable, with no automatic primary keys, defaults, or indexes. Creating a table requires an existing database and schema. Existing target tables are not overwritten. Saving or deleting a mapping alone does not alter or drop a physical table.

### Normalize rules

Open **Rules** to create, edit, test, and delete Normalize rules. Choose a Fields definition, a source string field, a condition, an action, and a destination string field. Rules have a name and optional description.

Conditions support starts with, ends with, contains, equals, and length equals/greater than/less than. Actions support copying, adding/removing a prefix or suffix, replacing text, trimming whitespace, and uppercase/lowercase conversion. Replace string uses literal, case-sensitive find text and replaces every occurrence; an empty replacement removes the find text. Only string/text fields are eligible. The current Fields editor uses `string` for text.

Java evaluates both sample tests and saved-rule application. When the condition matches, the transformed source replaces the destination value; otherwise the whole record stays unchanged. Missing, null, and non-string source values do not match. Saved rules protect their referenced Fields definition and field keys from incompatible edits. The Java engine applies these rules to File workflows, fusing consecutive normalization blocks into one ordered per-record pass.

### Dynamic: Mappers

Open **Dynamic → Mapper** to create, edit, and delete mapper configurations. Each mapper has a name, optional description, a WASM directory, a separate `.wasm` filename, and one or more output lists. Each output list has an exact, case-sensitive name with no spaces or whitespace and selects a saved Fields definition as its type. Names must be unique within the mapper; `CallRecords` and `callRecords` remain distinct.

The editor previews all field keys/types for each output. Saving snapshots those schemas and protects their Fields references from incompatible edits. Re-saving a mapper refreshes its snapshots from the selected definitions. The directory and filename are stored separately without changing them. The Java engine loads and validates modules when compiling a File workflow, then executes them through the shared WASM ABI.

### Dynamic: Transformers

Open **Dynamic → Transformer** to create, edit, and delete named transformer configurations with an optional description, WASM directory, and separate `.wasm` filename. Each transformer has one or more inputs and one or more outputs. Each named port is either a **Fields list** (a list of a selected Fields definition) or a **Dynamic variable** (a named JSON value at runtime).

Mix both kinds on either side, preview Fields schemas, and add or remove ports. Names are case sensitive and unique within inputs or outputs; an input and output may share a name. List schemas are saved with the configuration and protected from incompatible Fields edits. Dynamic variables do not require a Fields definition. The Java engine passes named list and dynamic inputs/outputs through the WASM ABI within each source-file exchange.

### Conditional rules

Open **Rules → Conditional** to create named rules with an optional description and a Fields definition. All field types are available. Compare a field against a typed value or a compatible field, and nest groups using **AND** (all children) or **OR** (any child). For example: `end_time > start_time AND (status = "completed" OR duration > 60)`.

Operators include equality, inequality, greater/less comparisons, text matching, and null checks as supported by each field type. Java evaluates saved rules and sample records, returning `matched` without changing the record. Dates use ISO dates; datetimes require a timezone offset. Rules protect every field referenced on either side of a comparison. The Java engine uses these predicates for matched/not-matched routing in File workflows.

### Reviewed column changes

For an existing table, **Review column changes** compares its actual PostgreSQL columns with the saved mapping and proposes **ADD**, **ALTER**, and **DROP COLUMN** statements.

- Changes start unselected. Review the SQL and choose the operations to apply.
- ALTER and DROP require explicit acknowledgment of possible data loss.
- The backend rebuilds the plan and rejects stale reviews or unknown change IDs.
- Selected operations run in one PostgreSQL transaction, with locking and timeouts. Conversion or dependency failures roll back the transaction.
- DROP does not use CASCADE. Partitioned/inherited tables and type changes to identity/generated columns require direct PostgreSQL management.

To change a used field, first remove it from **every** affected table mapping and save. Then edit the field, add it back to the mapping, save, and review the resulting database changes. To drop a column, remove it from the mapping, save, and select its DROP operation in the review. Renaming a field proposes separate ADD and DROP operations and does not copy existing values to the new column.

## Telecom demo

The Java startup `TelecomDemoSeeder` populates **Telecom - CSV to CDR and SMS**, a file-based workflow
with mixed/CDR/SMS field dictionaries, calling/called normalization rules,
conditional routing, projection transformer definitions, and separate CDR, SMS,
and rejected File destinations. Open it from **Workflows** after refreshing the UI.
See [the telecom example](examples/telecom/README.md) for the sample CSV, expected
routing, supported number formats, and configured paths. Saving keeps it inactive; use the [Java engine API](services/camel-integration/ENGINE.md) to validate and start the File workflow.

Rust source and compiled mapper/transformer modules are available in
[`wasm/`](wasm/README.md). Use `make wasm-build` to rebuild them or `make wasm-test`
to run native Rust and compiled WASM tests. They use named inputs/outputs matching
the telecom definitions. The [Java Camel engine](services/camel-integration/ENGINE.md) executes them in explicitly started File workflows.

## Workflow designer preview

Open **Workflows → New workflow** to start a React Flow canvas with a required **Data source** block. Select that block and choose an existing File or PostgreSQL connector in the Properties panel. Move blocks by dragging them. Add a **Next step** placeholder by dragging it from the block library or clicking it, then draw an arrow from the right-hand output dot to another block’s left-hand input dot.

The editor includes zoom/pan controls, a minimap, editable labels, selectable/deletable arrows, and guards against cycles, duplicate arrows, and incoming edges to the source. The initial source cannot be deleted. The Mapper block selects a saved mapper and shows one named output handle per defined output list, with its field schema available in Properties. Connect lists independently; different output lists may feed the same target. Sync outputs applies the latest definition, retaining arrows for matching list names and removing arrows for deleted lists. Conditional rule blocks select a saved rule and provide one input with Matched and Not matched outputs. Normalize rule blocks select a saved rule and provide one input and one Output. Properties preview the selected rule and Fields definition. Fixed rule ports retain their arrows when changing the selected definition. Mapper ports display their Fields type, and connections require the same saved Fields definition on both ends. Raw sources feed mapper inputs; rules consume typed outputs. Incompatible existing arrows are highlighted for correction before saving. Transformer blocks select saved definitions and expose every named input and output with its type. Fields lists connect to the same saved Fields definition, and dynamic variables connect only to dynamic variables. Sync ports preserves exact-name arrows and removes arrows for deleted ports. Saving configures the graph; the Java engine API can explicitly execute supported File workflows. Placeholder steps are rejected during execution compilation.

Add a **Data destination** block to select a saved connector that will receive data. It has one input on the left, accepts raw or typed payloads, and has no output.

**Save draft** stores the graph, positions, viewport, name/description, and connector references through the Java `/api/workflows` API in the platform database. Reopen, search/filter, update, or delete saved workflows from the list, or **Export JSON** to keep a separate copy. Unsaved changes are not persisted automatically. Existing browser drafts can be imported using **Import browser drafts**; their browser copies are kept and IDs already present on the server are skipped. Invalid drafts must be corrected before import. Server timestamps track creation and updates. Drafts contain connector references and definition snapshots rather than credentials or connector settings. Missing connector references remain editable and can be replaced in Properties. Saving does not start Camel execution.

## Searching saved items

Connectors, Fields, Tables, Normalize rules, Conditional rules, Mappers, and Transformers show creation/update timestamps and provide a search/filter toolbar. Search all supported details or choose a specific field, such as name or description where available. Filters combine with search, and include relevant types, creation-date ranges, and sorting by name, creation, or update time. Counts show matching versus total items; Clear filters restores the full list.

Date ranges use the browser's local calendar and include the entire end day. Connector creation time is now recorded for new records. Legacy connectors keep an unknown creation time because their original creation dates were not stored; they remain visible without date filters and are excluded from creation-date ranges. Existing entity timestamps are preserved on edits.

Filtering runs locally over the records already returned by each list API. Workflow drafts are stored in the platform database and use the same search controls. Executions has no persisted run history yet; the engine API exposes in-memory counters.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`apps/workflow-designer`](apps/workflow-designer) | React interface, connector editors, and schema/table utilities |
| [`services/camel-integration`](services/camel-integration) | Spring Boot APIs, Camel routes, validation, JPA persistence, and PostgreSQL operations |
| [`docker`](docker) | PostgreSQL 17 and pgAdmin Compose setup |
| [`docs`](docs) | Project notes and Camel reference material |

Platform configuration is persisted in PostgreSQL and versioned with Flyway migrations. JPA handles internal configuration storage; JDBC connects to user-selected PostgreSQL targets. Configuration storage and target database connections are separate concerns, even when they use the same PostgreSQL instance.

## Run locally

Prerequisites: Java 21 or newer, Node.js 24 with npm, and Docker with Docker Compose. The backend includes a Maven wrapper.

From the repository root, create the local environment file if it does not already exist:

```sh
cp .env.example .env
```

Edit `.env` with your PostgreSQL and pgAdmin settings, then start the database services:

```sh
docker compose --env-file .env -f docker/compose.postgres.yml up -d
```

Start the backend in one terminal:

```sh
cd services/camel-integration
./mvnw spring-boot:run
```

Start the frontend in another terminal:

```sh
cd apps/workflow-designer
npm ci
npm run dev
```

Open the URL printed by Vite and select **Connectors**. The backend listens on port **8081**, and Vite proxies `/api` to it. Flyway applies migrations, including the CDR sample, when the backend starts. pgAdmin is available at **http://localhost:5050** by default; its login is separate from the database credentials.

The backend reads the root `.env` when launched from the repository root or the service directory. Environment variables can override its settings, including `POSTGRES_JDBC_URL` for platform configuration storage.

## Makefile shortcuts

From the repository root, use `make help` to list the development shortcuts. These commands require Make and the same Java, Node.js, and Docker tools described above.

```sh
make frontend-install
make docker
```

Then run `make backend` and `make frontend` in separate terminals. Both development servers run in the foreground; use Ctrl+C to stop them. Docker services run in the background.

| Command | Action |
| --- | --- |
| `make frontend-install` | Install frontend dependencies with `npm ci` |
| `make frontend` | Run the Vite frontend |
| `make backend` | Run Spring Boot using the selected environment file |
| `make docker` or `make docker-up` | Start PostgreSQL and pgAdmin |
| `make docker-down` | Stop Docker services while preserving data volumes |
| `make docker-logs` | Follow Docker service logs |
| `make docker-ps` | Show Docker service status |
| `make ollama-serve` | Run Ollama locally, reachable from the WebUI container |
| `make ollama` or `make ollama-gemma` | Run Gemma (`gemma4:e4b-it-q4_K_M`) locally |
| `make ollama-qwen` | Run Qwen (`qwen3.5:9b`) locally |
| `make ollama MODEL=...` | Run another model with local Ollama |
| `make webui` or `make webui-up` | Start only Open WebUI in Docker |
| `make webui-down` | Stop WebUI while preserving its data volume |
| `make webui-logs` / `make webui-ps` | Follow WebUI logs / show status |

See [Ollama and Open WebUI setup](docker/README.md#ollama-and-open-webui) for the local server and WebUI workflow. WebUI opens at http://localhost:3000.

Backend and Docker shortcuts use the root `.env` by default. Select another file with `ENV_FILE`, using a path relative to the repository root or an absolute path:

```sh
make docker ENV_FILE=.env.local
make backend ENV_FILE=.env.local
make docker-ps ENV_FILE=.env.local
make docker-down ENV_FILE=.env.local
```

Use the same environment file for the backend and Docker services. The backend shortcut explicitly imports the chosen file as Spring properties, replacing its default `.env` import paths. Environment variables can still override properties from the file. Docker commands always pass the selected path through `--env-file`. Missing files produce an error before either command starts. Choosing another file does not create a separate Docker stack or reset existing volumes.

`ENV_FILE` applies to backend and Docker shortcuts. The frontend uses Vite's own environment handling; the Makefile does not load database credentials into the frontend process.

## Validation

Backend tests use an isolated H2 database for persistence and migration checks, and mocked JDBC connections for PostgreSQL table creation and schema-change behavior:

```sh
cd services/camel-integration
./mvnw test
```

Frontend checks:

```sh
cd apps/workflow-designer
npm run build
npm run lint
```

For the shared Java and frontend formatting tools, run `npm ci` at the repository root, then `npm run format:check` or `npm run format`.

## Current scope and documentation

Connections, Fields, Tables, rules, Mappers, Transformers, and database-backed workflow drafts have working editors and Java APIs. The Java Camel engine executes File workflows with real WASM modules through explicit start/stop APIs. PostgreSQL execution, engine UI controls, streaming/batching, and persisted execution history remain under development.

This implementation targets a trusted local workspace. The API has no authentication, and connection passwords are stored in plaintext in the configuration database. Keep `.env` out of version control and add authentication and secret storage before exposing the service to other users.

- [Backend APIs, payloads, migrations, and column-change workflow](services/camel-integration/README.md)
- [Frontend setup and source layout](apps/workflow-designer/README.md)
- [PostgreSQL and pgAdmin setup](docker/README.md)
