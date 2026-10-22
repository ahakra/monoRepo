import Sidebar from './components/sidebar/Sidebar.js'
import Dashboard from './pages/dashboard/Dashboard.js'
import Workflows from './pages/workflows/Workflows.js'
import Connectors from './pages/connectors/Connectors.js'
import Executions from './pages/executions/Executions.js'
import Logs from './pages/logs/Logs.js'
import Settings from './pages/settings/Settings.js'
import './App.css'

const pages = { dashboard: Dashboard, workflows: Workflows, connectors: Connectors, executions: Executions, logs: Logs, settings: Settings }

export default function AppTemplate({ activePage, navigation }) {
  const Page = pages[activePage]
  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} navigation={navigation} />
      <Page />
    </div>
  )
}
