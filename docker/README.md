# PostgreSQL and pgAdmin

From the repository root, create `.env` from `.env.example` if needed and set the PostgreSQL and pgAdmin credentials. The pgAdmin login is separate from the database login.

```sh
docker compose --env-file .env -f docker/compose.postgres.yml up -d
```

Open http://localhost:5050 (or the `PGADMIN_PORT` in `.env`). Sign in using `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`.

In pgAdmin, choose **Add New Server**:

- General → Name: `Camel ETL`
- Connection → Host: `postgres` (the Compose service hostname)
- Port: `5432`
- Maintenance database: the value of `POSTGRES_DB`
- Username: the value of `POSTGRES_USER`
- Password: the value of `POSTGRES_PASSWORD`

Expand **Databases → your database → Schemas → public → Tables** to browse the tables.

pgAdmin settings persist in the `pgadmin_data` volume. Its default email/password initialize the account on first startup; for an existing account, change the password through pgAdmin. The web interface is bound to localhost.

The container configuration follows the [official pgAdmin deployment documentation](https://www.pgadmin.org/docs/pgadmin4/latest/container_deployment.html).
