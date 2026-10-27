# Workflow designer

React + Vite frontend for the Camel ETL platform.

```bash
npm install
npm run dev
```

`npm run build` creates the production bundle. `npm run lint` checks the source.

Pages live in `src/pages/`; reusable UI such as the sidebar lives in
`src/components/`. Each component keeps its logic and markup together in a
`.jsx` file, with a separate `.css` file for styles. Global theme tokens live
in `src/index.css`. Sidebar entries are defined in `src/navigation.js`, and
page components are registered in `src/App.jsx`.

Home is the default page. The sidebar also links to Dashboard, Workflows,
Configuration, Connectors, Executions, Logs, and Settings. Hash navigation supports
reloads and browser back/forward without server routing configuration.

Configuration integrates with the Java backend to manage PostgreSQL connections,
reusable Fields definitions, and Tables. It includes field-usage protections,
table creation, and reviewed ADD/ALTER/DROP column operations. Other sections
remain under development; the separate Connectors page is a placeholder.

Vite proxies `/api` to the backend on port 8081. See the
[repository overview](../../README.md) for local setup and the
[backend guide](../../services/camel-integration/README.md) for API details and
the field/table editing workflow.
