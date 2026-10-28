# Workflow designer

React + Vite frontend for the Camel ETL platform.

```bash
npm install
npm run dev
```

`npm run build` creates the production bundle. `npm run lint` checks the source.
`npm test` checks rule builders, mapper/transformer configuration, saved-item filters, and type-specific connector serialization.

Pages live in `src/pages/`; reusable UI such as the sidebar lives in
`src/components/`. Each component keeps its logic and markup together in a
`.jsx` file, with a separate `.css` file for styles. Global theme tokens live
in `src/index.css`. Sidebar entries are defined in `src/navigation.js`, and
page components are registered in `src/App.jsx`.

Home remains the default landing page. The sidebar lists these sections in order:

1. **Connectors** — File, FTP/SFTP, SQL, and APIs.
2. **Dynamic** — Mapper and Transformer configuration.
3. **Workflows** — Design and configure execution flows.
4. **Executions** — Run history, monitoring, logs, and retries.
5. **Schemas & Tables** — Optional field definitions and database table creation.
6. **Rules** — Reusable rules for ETL workflows.

Hash navigation supports reloads and browser back/forward without server routing
configuration. Existing `#configuration` links open Schemas & Tables, and
`#mappers` links open Dynamic.

Connectors integrates with the Java backend to manage PostgreSQL and File
connections. File uses Camel option keys, with source/processed/failed directories
and advanced filters, polling, and read-lock settings. Test directories performs
access checks without creating folders or consuming files. PostgreSQL advanced
settings expose schema/search path, connection and socket timeouts in seconds,
and an SSL root certificate path. Values are persisted in connector settings
and used by the Java connection factory.
Schemas & Tables contains reusable Fields definitions and Tables, including
field-usage protections, table creation, and reviewed ADD/ALTER/DROP column
operations. Rules provides a Normalize editor with string/text field selectors,
conditions, actions, and sample testing through the Java API. The Conditional
editor includes all field types, typed values or compatible field comparisons,
nested AND/OR groups, and sample record testing in Java. Rule type tabs preserve
unsaved drafts when switching between Normalize and Conditional. Dynamic contains
Mapper and Transformer tabs that also preserve drafts. Mapper provides WASM
directory and filename configuration with named, typed output lists. Transformer
provides WASM directory/filename configuration and multiple named inputs and
outputs, each a Fields list or an untyped
dynamic variable. Fields lists show schema previews. Transformer evaluation is
not implemented. Other connector types, WASM execution, Workflows, and
Executions remain under development.

Vite proxies `/api` to the backend on port 8081. See the
[repository overview](../../README.md) for local setup and the
[backend guide](../../services/camel-integration/README.md) for API details and
the field/table editing workflow.

Saved-item lists share `src/components/items/` controls for scoped text search,
page-specific type facets, creation date ranges, sorting, match counts, and clear
filters. Controls keep their state during editing and tab switches. Each row
shows creation and update times in the browser’s timezone. Date range end dates
are inclusive. Unknown legacy connector creation dates sort last and do not
match creation-date filters. Searches use explicit public-field selectors;
connector secrets are excluded. Filtering is local to loaded list results.
