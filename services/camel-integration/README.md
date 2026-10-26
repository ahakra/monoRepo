# Connection configuration

Start PostgreSQL from the repository root:

```sh
docker compose --env-file .env -f docker/compose.postgres.yml up -d
```

Create `.env` from `.env.example` first if needed (`cp .env.example .env` from the repository root). Compose requires nonempty `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. Camel reads the same root `.env` when launched from the repository root or this service directory; environment variables can override those values. The default JDBC URL uses `localhost:5432` and `POSTGRES_DB`. Set `POSTGRES_JDBC_URL` for another database host or port.

If Java fails while initializing Flyway, check `docker compose --env-file .env -f docker/compose.postgres.yml ps` and container logs from the repository root. A restarting container with “superuser password is not specified” means Compose was started without the settings. Rerun the command above with `--env-file .env`; the existing data volume is preserved. If port 8081 is occupied, check whether an IDE instance is already serving the API before starting a second instance.

Run `./mvnw spring-boot:run` in this directory, and `npm run dev` in `apps/workflow-designer`. Open **Configuration** in the sidebar. The Vite development server proxies `/api` to Camel on port 8081. Production hosting must also proxy `/api` to Camel.

Flyway creates `etl_connections` and `etl_connection_settings`; JPA maps these tables and validates their schema. Existing `integration_connections` rows are migrated once; the legacy table is retained as a backup. Saved configurations survive restarts through the existing Docker volume. The target connection host is resolved from the Camel process: use `localhost` when running Camel on the host, or `postgres` on the same Docker network.

API:

- `GET /api/connections`: list configurations, excluding passwords.
- `POST /api/connections`: create a configuration.
- `PUT /api/connections/{id}`: update; blank or omitted password preserves the saved password.
- `DELETE /api/connections/{id}`: delete a configuration.
- `POST /api/connections/test`: test unsaved fields.
- `POST /api/connections/{id}/test`: test edited fields, reusing the saved password when blank.

Create, update, and test accept `name`, `type`, `password`, and a type-specific `settings` map. For example:

```json
{
  "name": "Analytics destination",
  "type": "postgres",
  "password": "your-password",
  "settings": {
    "host": "localhost",
    "port": "5432",
    "database": "analytics",
    "username": "camel_etl",
    "sslMode": "disable",
    "createDatabaseIfNotExists": "true"
  }
}
```

Settings values are strings. PostgreSQL supports SSL modes `disable`, `require`, and `verify-full`. Saving only persists configuration. With `createDatabaseIfNotExists` enabled, **Create & test** first tries the target database. Only a missing-database error triggers creation through the `postgres` maintenance database, followed by another connection test. Existing databases do not require creation privileges. The account must have `CREATEDB` to create a missing database; creation runs outside a transaction as required by [PostgreSQL](https://www.postgresql.org/docs/current/sql-createdatabase.html). Turning the option off never creates a database. Configurations are not yet attached to pipeline execution.

The Java packages separate responsibilities:

- `api`: HTTP controller, request/response DTOs, error mapping.
- `application`: framework-independent connection use cases and commands.
- `domain`: connection model, persistence port, and connection type handler contract.
- `infrastructure/persistence`: JPA entity, Spring Data repository, and persistence adapter.
- `infrastructure/postgres`: PostgreSQL validation, JDBC connection factory, and connection testing/creation.

To add another type, register a `ConnectionTypeHandler` bean with its own settings validation and testing behavior, and add its frontend form. The application service dispatches by type; the entity and repository have no database-specific fields. MySQL and HTTP are extension points, not implemented types.

This first iteration is for a trusted local workspace: the API has no authentication and passwords are stored as plaintext in the configuration database, although never returned by the API. Restrict access to the service and database. Add authentication and a secrets store before exposing it to other users.

Run `./mvnw test` for service tests (isolated H2 test database) and `npm run build` / `npm run lint` in the frontend directory.


Formatting is configured at the repository root. Run `npm ci` there once, then `npm run format` to format the Java and frontend sources, or `npm run format:check` to check them. Java uses four-space indentation; JavaScript, JSX, and CSS use two spaces.
