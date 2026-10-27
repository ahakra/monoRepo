# Camel ETL

An integration and ETL platform built with **Spring Boot + Apache Camel** and a **React + Vite** interface. The current implementation provides reusable PostgreSQL connections, typed field dictionaries, table mappings, and reviewed PostgreSQL table and column operations.

## Features

### Connections

Use **Configuration → Connections** to create, edit, test, and delete saved connector configurations. PostgreSQL is the implemented connection type.

- Configure the host, port, database, username, password, and SSL mode.
- Reuse saved connections when defining tables.
- Test a connection before using it.
- Optionally create a missing database with **Create & test**, when the database user has the required permission.
- Keep the existing password when an edit leaves the password blank. Passwords are excluded from API responses.

Saving a connection stores its configuration. Testing and optional database creation are separate actions. A connection referenced by a table configuration cannot be deleted.

The sidebar's separate **Connectors** page is a placeholder; implemented connection management is under **Configuration**. Additional connector types and pipeline integration are future work.

### Fields

Use **Configuration → Fields** to define named, reusable dictionaries of field keys and logical types:

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

Use **Configuration → Tables** to map fields to a target PostgreSQL table:

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

### Reviewed column changes

For an existing table, **Review column changes** compares its actual PostgreSQL columns with the saved mapping and proposes **ADD**, **ALTER**, and **DROP COLUMN** statements.

- Changes start unselected. Review the SQL and choose the operations to apply.
- ALTER and DROP require explicit acknowledgment of possible data loss.
- The backend rebuilds the plan and rejects stale reviews or unknown change IDs.
- Selected operations run in one PostgreSQL transaction, with locking and timeouts. Conversion or dependency failures roll back the transaction.
- DROP does not use CASCADE. Partitioned/inherited tables and type changes to identity/generated columns require direct PostgreSQL management.

To change a used field, first remove it from **every** affected table mapping and save. Then edit the field, add it back to the mapping, save, and review the resulting database changes. To drop a column, remove it from the mapping, save, and select its DROP operation in the review. Renaming a field proposes separate ADD and DROP operations and does not copy existing values to the new column.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`apps/workflow-designer`](apps/workflow-designer) | React interface and Configuration editors |
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

Open the URL printed by Vite and select **Configuration**. The backend listens on port **8081**, and Vite proxies `/api` to it. Flyway applies migrations, including the CDR sample, when the backend starts. pgAdmin is available at **http://localhost:5050** by default; its login is separate from the database credentials.

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

Connections, Fields, and Tables have working frontend editors and Java APIs. Pipeline execution is not yet connected to these configurations; the backend currently includes a sample Camel route. The Workflows, Connectors, and other application sections are still under development.

This implementation targets a trusted local workspace. The API has no authentication, and connection passwords are stored in plaintext in the configuration database. Keep `.env` out of version control and add authentication and secret storage before exposing the service to other users.

- [Backend APIs, payloads, migrations, and column-change workflow](services/camel-integration/README.md)
- [Frontend setup and source layout](apps/workflow-designer/README.md)
- [PostgreSQL and pgAdmin setup](docker/README.md)
