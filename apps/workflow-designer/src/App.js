import { useEffect, useState, createElement } from 'react'
import AppTemplate from './App.template.jsx'

export const navigation = [
  { id: 'dashboard', label: 'Dashboard', icon: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z' },
  { id: 'workflows', label: 'Workflows', icon: 'M4 3h6v6H4zM14 15h6v6h-6zM7 9v6a3 3 0 0 0 3 3h4' },
  { id: 'connectors', label: 'Connectors', icon: 'M9 3v5m6-5v5M7 8h10v4a5 5 0 0 1-10 0V8Zm5 9v4' },
  { id: 'executions', label: 'Executions', icon: 'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM5 13h3l3-6 3 10 3-6h2' },
  { id: 'logs', label: 'Logs', icon: 'M6 3h14v18H6zM3 6v12h3M9 7h8M9 11h8M9 15h6' },
  { id: 'settings', label: 'Settings', icon: 'm12 3 3 2 3 .5.5 3 2 3-2 3-.5 3-3 .5-3 2-3-2-3-.5-.5-3-2-3 2-3 .5-3L9 5Z M15 11.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0' },
]

function readPage() {
  const id = window.location.hash.slice(1)
  return navigation.some((item) => item.id === id) ? id : 'dashboard'
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

  return createElement(AppTemplate, { activePage, navigation })
}
