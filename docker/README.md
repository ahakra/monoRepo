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

# Ollama and Open WebUI

Install Ollama on the host and set `WEBUI_SECRET_KEY` to a long random value in the root `.env` (see `.env.example`). From the repository root:

```sh
# Terminal 1: local Ollama server, reachable from the WebUI container.
make ollama-serve

# Terminal 2: download the model if needed and start a terminal chat.
make ollama-gemma
# Or: make ollama-qwen

# Terminal 3: start only the WebUI container.
make webui
```

Open http://localhost:3000 and select your model. `make ollama` defaults to `gemma4:e4b-it-q4_K_M`. Use `make ollama-gemma` or `make ollama-qwen` for your installed models, or `make ollama MODEL=...` for another model. The model remains available to WebUI after you exit the terminal chat.

If Ollama is already running, use the existing server instead of starting another one. It must listen on an address reachable from Docker. `make ollama-serve` sets `OLLAMA_HOST=0.0.0.0:11434`, which exposes the API on host network interfaces; use a trusted network. For an existing system service, configure `OLLAMA_HOST` in that service and restart it, following the [Ollama FAQ](https://docs.ollama.com/faq). Override the Make target's bind address with `OLLAMA_BIND` if needed.

`make webui` runs the following Compose command, leaving PostgreSQL and pgAdmin separate:

```sh
docker compose --env-file .env -f docker/compose.webui.yml up -d open-webui
```

Use `make webui-logs`, `make webui-ps`, and `make webui-down` to manage WebUI. Its data volume is kept when stopped. These targets support `ENV_FILE=.env.local` just like the PostgreSQL targets. Stop a foreground Ollama server with Ctrl+C.
