import { useEffect, useState } from 'react'
import { navigation } from './navigation.js'
import Sidebar from './components/sidebar/Sidebar.jsx'
import Home from './pages/home/Home.jsx'
import Workflows from './pages/workflows/Workflows.jsx'
import Connectors from './pages/connectors/Connectors.jsx'
import Executions from './pages/executions/Executions.jsx'
import SchemasTables from './pages/configuration/SchemasTables.jsx'
import Mappers from './pages/mappers/Mappers.jsx'
import Rules from './pages/rules/Rules.jsx'
import './App.css'

const pages = {
  home: Home,
  'schemas-tables': SchemasTables,
  mappers: Mappers,
  workflows: Workflows,
  connectors: Connectors,
  executions: Executions,
  rules: Rules,
}

function readPage() {
  const hash = window.location.hash.slice(1)
  const id = hash === 'configuration' ? 'schemas-tables' : hash
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
      <Page />
    </div>
  )
}
