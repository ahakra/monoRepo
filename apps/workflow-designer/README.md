# Workflow designer

React + Vite frontend for the Camel ETL platform.

```bash
npm install
npm run dev
```

`npm run build` creates the production bundle. `npm run lint` checks the source.

Pages live in `src/pages/`; reusable UI such as the sidebar lives in
`src/components/`. Both use separate `.js` component entry points,
`.template.jsx` markup, and `.css` styles. JSX is React's template syntax;
standalone Angular-style HTML templates are not used. Global theme tokens
live in `src/index.css`.

The sidebar links to Dashboard, Workflows, Connectors, Executions, Logs, and
Settings. Dashboard is the default page. Hash navigation supports reloads and
browser back/forward without server routing configuration. New pages are
placeholders; Connectors remains empty. No backend integration is implemented.
