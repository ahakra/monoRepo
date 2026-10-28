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
not implemented. The Workflows page uses React Flow for a visual draft editor. Other connector
types, WASM execution, workflow execution, and Executions remain under development.

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

## Workflow editor (frontend only)

`@xyflow/react` supplies the controlled graph canvas. Workflows is loaded on demand
so the editor library does not increase the initial page bundle. A new workflow
starts with exactly one required Data source block, whose connector is selected
from the existing read-only `/api/connections` list.

The block palette includes a temporary Next step placeholder for trying drag/drop
and arrow connections; processing block types will be defined one by one. Click
to add is available alongside desktop drag/drop. Blocks can be moved and renamed;
arrows can be selected/deleted. Graph validation prevents missing targets,
self-edges, duplicate arrows, cycles, or incoming edges to the initial source.
Deleting a step removes its incident edges; the source is protected.

Save draft stores versioned definitions in `localStorage` under
`camel-etl.workflow-drafts.v1`. Only names, descriptions, timestamps, node positions,
connector IDs/names/types, edges, and viewport are persisted. Rendering state and
connector credentials/settings are stripped. Local drafts can be reopened,
searched, sorted, and deleted. Export JSON downloads a single definition; import
is not implemented yet. Unsaved changes are not automatically persisted.

Unreadable/version-mismatched drafts are reported without overwriting storage;
unavailable storage/quota errors leave the graph open for JSON export. Node/edge
changes remain local: no workflow backend, Java changes, deployment, or execution
is implemented. The model tests cover graph guards, source protection, edge
cleanup, serialization, restored drafts, and failed/corrupt browser storage.

### Mapper workflow block

Mapper is available in the workflow palette by drag/drop or click-to-add. It has
one input and selects an existing `/api/mappers` definition (read only). Each saved
output list becomes its own named source handle; the card grows to show every
output. Properties show output names and field-type snapshots. Node internals
are updated after dynamic handle changes.

Source handle IDs are derived from exact, case-sensitive list names using URI
encoding. Connection validation checks handle existence, with duplicate detection
per handle pair, so different mapper lists may feed the same target. Arrows are
labeled with output list names. Serialization preserves named handle IDs rather
than replacing every output with `out`; previous source/placeholder-only drafts
remain compatible.

Selecting or syncing a mapper updates the output snapshot. List names retained by
the new definition keep their arrows, while removed/renamed lists lose outgoing
arrows. Input arrows remain connected. Refresh mappers only reloads the definition
catalog; Sync outputs explicitly applies it to the selected block. Missing mapper
definitions retain their saved snapshots for offline draft editing and show an
inspector message. No mapper execution or new Java backend is implemented.

### Rule workflow blocks

Conditional rule has one input and two named outputs: Matched and Not matched.
Normalize rule has one input and one Output. Drag either from the palette or
click to add, then select a saved rule in Properties. The inspector previews the
definition and associated Fields. Branches connect independently, including to
the same target, and arrow labels identify each branch.

Rule IDs, names, and Fields references persist with local drafts and JSON exports.
Changing or clearing a rule keeps its fixed ports and existing arrows. Missing
definitions show an inspector message; Refresh rules reloads the catalog.
The editor reads existing rule and Fields APIs only. Evaluation, routing, and
normalization execution are not implemented in workflows; no Java changes are included.

### Workflow Fields type compatibility

Mapper output ports show their saved Fields type name (or its ID when the Fields
catalog is unavailable), alongside the exact output list name. Rule blocks show
their Fields type too. A typed connection requires the identical saved Fields
definition ID, not a similar name or a coincidentally matching set of fields.
Select a rule before connecting it. Conditional branches and Normalize output
retain the rule's Fields type. Placeholders pass types through, so adding them
cannot join incompatible types or merge lists of different types.

Data source output and mapper input carry raw data. Connect the source to a mapper
before using typed rules. Existing drafts remain readable; incompatible arrows
are highlighted and must be corrected or explicitly removed before save/export.
Changing a rule or syncing a mapper rechecks retained arrows without silently
removing type mismatches. Type validation applies during drag connections and
serialization and needs no Java changes.

### Transformer workflow block

Drag or click Transformer in the workflow palette, then choose an existing
`/api/transformers` definition. Every defined input and output becomes a separate
named handle. Cards and Properties show each port as a list of its saved Fields
type or a dynamic variable; Properties also show the list schema and WASM path.

List connections require the identical Fields definition ID. Dynamic-variable
ports connect only to other dynamic-variable ports; variable value types and
runtime evaluation remain undefined. Each input is validated independently, so
a transformer can accept several different list types without merging them.
Named target handles survive local draft saves and JSON exports. Duplicate,
self-connection, and cycle guards apply to these ports too.

Refresh transformers reloads the catalog without changing the graph. Sync ports
explicitly updates both input/output snapshots, preserving exact-name connections
and removing arrows for deleted or renamed ports. Retained arrows with changed
types are highlighted for correction before saving. Missing definitions retain
their saved snapshots for offline editing. No Java backend or WASM execution was
added; the existing definition APIs are read only.
