import { useState } from 'react'
import Fields from './Fields.jsx'
import Tables from './Tables.jsx'
import './Configuration.css'

export default function SchemasTables() {
  const [section, setSection] = useState('fields')
  return (
    <main className="page config" aria-labelledby="schemas-tables-title">
      <div className="config-eyebrow">WORKSPACE / SCHEMAS & TABLES</div>
      <header className="config-header">
        <div>
          <h1 id="schemas-tables-title">Schemas &amp; Tables</h1>
          <p>Optional utilities for reusable field definitions and database table creation.</p>
        </div>
      </header>
      <nav className="config-tabs" aria-label="Schema and table utilities">
        {['fields', 'tables'].map((id) => (
          <button
            key={id}
            aria-current={section === id ? 'page' : undefined}
            onClick={() => setSection(id)}
          >
            {{ fields: 'Fields', tables: 'Tables' }[id]}
          </button>
        ))}
      </nav>
      <div hidden={section !== 'fields'}>
        <Fields active={section === 'fields'} />
      </div>
      <div hidden={section !== 'tables'}>
        <Tables active={section === 'tables'} />
      </div>
    </main>
  )
}
