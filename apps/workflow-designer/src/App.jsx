import { useEffect, useState } from 'react'
import { navigation } from './navigation.js'
import Sidebar from './components/sidebar/Sidebar.jsx'
import Home from './pages/home/Home.jsx'
import Dashboard from './pages/dashboard/Dashboard.jsx'
import Workflows from './pages/workflows/Workflows.jsx'
import Connectors from './pages/connectors/Connectors.jsx'
import Executions from './pages/executions/Executions.jsx'
import Logs from './pages/logs/Logs.jsx'
import Configuration from './pages/configuration/Configuration.jsx'
import Settings from './pages/settings/Settings.jsx'
import './App.css'

const pages = {
  home: Home,
  configuration: Configuration,
  dashboard: Dashboard,
  workflows: Workflows,
  connectors: Connectors,
  executions: Executions,
  logs: Logs,
  settings: Settings,
}

function readPage() {
  const id = window.location.hash.slice(1)
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
