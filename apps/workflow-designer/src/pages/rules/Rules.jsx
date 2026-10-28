import { useState } from 'react'
import NormalizeRules from './NormalizeRules.jsx'
import ConditionalRules from './ConditionalRules.jsx'
import '../configuration/Configuration.css'
import './Rules.css'

export default function Rules() {
  const [type, setType] = useState('normalize')
  return (
    <main className="page config rules-page" aria-labelledby="rules-title">
      <div className="config-eyebrow">WORKSPACE / RULES</div>
      <header className="config-header">
        <div>
          <h1 id="rules-title">Rules</h1>
          <p>Build reusable normalization and conditional rules for your data.</p>
        </div>
      </header>
      <nav className="config-tabs" aria-label="Rule types">
        <button
          aria-current={type === 'normalize' ? 'page' : undefined}
          onClick={() => setType('normalize')}
        >
          Normalize
        </button>
        <button
          aria-current={type === 'conditional' ? 'page' : undefined}
          onClick={() => setType('conditional')}
        >
          Conditional
        </button>
      </nav>
      <div hidden={type !== 'normalize'}>
        <NormalizeRules />
      </div>
      <div hidden={type !== 'conditional'}>
        <ConditionalRules />
      </div>
    </main>
  )
}
