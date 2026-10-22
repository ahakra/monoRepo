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

The sidebar links to Dashboard, Workflows, Connectors, Executions, Logs, and
Settings. Dashboard is the default page. Hash navigation supports reloads and
browser back/forward without server routing configuration. All pages are
placeholders. No backend integration is implemented.
