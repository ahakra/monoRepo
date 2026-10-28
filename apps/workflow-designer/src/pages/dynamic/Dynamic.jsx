import { useState } from 'react'
import Mappers from '../mappers/Mappers.jsx'
import Transformers from './Transformers.jsx'
import '../configuration/Configuration.css'
import '../mappers/Mappers.css'

export default function Dynamic() {
  const [section, setSection] = useState('mapper')
  return (
    <main className="page config mappers-page" aria-labelledby="dynamic-title">
      <div className="config-eyebrow">WORKSPACE / DYNAMIC</div>
      <header className="config-header">
        <div>
          <h1 id="dynamic-title">Dynamic</h1>
          <p>Configure mappers and transformers for your data flows.</p>
        </div>
      </header>
      <nav className="config-tabs" aria-label="Dynamic sections">
        <button
          aria-current={section === 'mapper' ? 'page' : undefined}
          onClick={() => setSection('mapper')}
        >
          Mapper
        </button>
        <button
          aria-current={section === 'transformer' ? 'page' : undefined}
          onClick={() => setSection('transformer')}
        >
          Transformer
        </button>
      </nav>
      <div hidden={section !== 'mapper'}>
        <Mappers />
      </div>
      <div hidden={section !== 'transformer'}>
        <Transformers />
      </div>
    </main>
  )
}
