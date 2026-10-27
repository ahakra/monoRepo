import { useState } from 'react'
import Connections from './Connections.jsx'
import Fields from './Fields.jsx'
import Tables from './Tables.jsx'
import './Configuration.css'

export default function Configuration() {
  const [section, setSection] = useState('connections')
  return (
    <main className="page config" aria-labelledby="configuration-title">
      <div className="config-eyebrow">WORKSPACE / CONFIGURATION</div>
      <header className="config-header">
        <div>
          <h1 id="configuration-title">Configuration</h1>
          <p>Manage reusable connections, field definitions, and tables for your ETL workflows.</p>
        </div>
      </header>
      <nav className="config-tabs" aria-label="Configuration sections">
        {['connections', 'fields', 'tables'].map((id) => (
          <button
            key={id}
            aria-current={section === id ? 'page' : undefined}
            onClick={() => setSection(id)}
          >
            {{ connections: 'Connections', fields: 'Fields', tables: 'Tables' }[id]}
          </button>
        ))}
      </nav>
      <div hidden={section !== 'connections'}>
        <Connections />
      </div>
      <div hidden={section !== 'fields'}>
        <Fields active={section === 'fields'} />
      </div>
      <div hidden={section !== 'tables'}>
        <Tables active={section === 'tables'} />
      </div>
    </main>
  )
}
