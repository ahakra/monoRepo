import { lazy, Suspense, useEffect, useState } from 'react'
import { navigation } from './navigation.js'
import Sidebar from './components/sidebar/Sidebar.jsx'
import Home from './pages/home/Home.jsx'
import Connectors from './pages/connectors/Connectors.jsx'
import Executions from './pages/executions/Executions.jsx'
import SchemasTables from './pages/configuration/SchemasTables.jsx'
import Dynamic from './pages/dynamic/Dynamic.jsx'
import Rules from './pages/rules/Rules.jsx'
import './App.css'

const Workflows = lazy(() => import('./pages/workflows/Workflows.jsx'))

const pages = {
  home: Home,
  'schemas-tables': SchemasTables,
  dynamic: Dynamic,
  workflows: Workflows,
  connectors: Connectors,
  executions: Executions,
  rules: Rules,
}

function readPage() {
  const hash = window.location.hash.slice(1)
  const aliases = { configuration: 'schemas-tables', mappers: 'dynamic' }
  const id = aliases[hash] || hash
  return navigation.some((item) => item.id === id) ? id : 'home'
}

export default function App() {
  const [activePage, setActivePage] = useState(readPage)

  useEffect(() => {
    const updatePage = () => setActivePage(readPage())
    window.addEventListener('hashchange', updatePage)
    return () => window.removeEventListener('hashchange', updatePage)
  }, [])

  useEffect(() => {
    const page = navigation.find((item) => item.id === activePage)
    document.title = `${page.label} | Camel ETL`
  }, [activePage])

  const Page = pages[activePage]
  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} navigation={navigation} />
      <Suspense
        fallback={
          <main className="page" role="status">
            Loading page…
          </main>
        }
      >
        <Page />
      </Suspense>
    </div>
  )
}
