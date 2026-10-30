import { useEffect, useState } from 'react'
import { workflowsApi } from '../workflows/workflowsApi.js'
import { useWorkflowEngine } from '../workflows/useWorkflowEngine.js'
import WorkflowActivity from '../workflows/WorkflowActivity.jsx'
import WorkflowSourceSettings from '../workflows/WorkflowSourceSettings.jsx'
import '../configuration/Configuration.css'
import '../workflows/Workflows.css'
import './Executions.css'

export default function Executions({ location }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const requested = new URLSearchParams(location?.search || '').get('workflow')
  useEffect(() => {
    let active = true
    workflowsApi()
      .then((workflows) => {
        if (active) {
          setItems(workflows)
          setError('')
        }
      })
      .catch((error) => {
        if (active) setError(error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [reload])
  const selected = items.find((item) => item.id === requested) || items[0]
  return (
    <main className="page config executions-page" aria-labelledby="executions-title">
      <div className="config-eyebrow">WORKSPACE / EXECUTIONS</div>
      <header className="config-header">
        <div>
          <h1 id="executions-title">Executions</h1>
          <p>Review imported batches. Expand a batch to inspect its stages and logs.</p>
        </div>
        <button
          disabled={loading}
          onClick={() => {
            setLoading(true)
            setReload((value) => value + 1)
          }}
        >
          Refresh workflows
        </button>
      </header>
      {error && (
        <div className="config-notice error" role="status">
          {error}
        </div>
      )}
      {loading ? (
        <p role="status">Loading workflows…</p>
      ) : !items.length ? (
        <div className="config-panel">
          <p>No workflows available.</p>
          <a href="#workflows">Create a workflow</a>
        </div>
      ) : (
        <>
          <label className="execution-workflow-picker">
            Workflow
            <select
              value={selected.id}
              onChange={(event) => {
                window.location.hash = `executions?workflow=${encodeURIComponent(event.target.value)}`
              }}
            >
              {items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <ExecutionDetails key={selected.id} workflow={selected} />
        </>
      )}
    </main>
  )
}

function ExecutionDetails({ workflow }) {
  const engine = useWorkflowEngine(workflow.id, true)
  const openFlow = (node, context) => {
    const query = new URLSearchParams({ workflow: workflow.id })
    if (node) query.set('node', node)
    if (context?.runId) query.set('run', context.runId)
    if (context?.exchangeId) query.set('file', context.exchangeId)
    if (context?.executionId) query.set('batch', context.executionId)
    window.location.hash = `workflows?${query}`
  }
  return (
    <>
      <section className="execution-overview" aria-label="Workflow execution status">
        <div>
          <h2>{workflow.name}</h2>
          <p>
            {engine.status?.running
              ? engine.status.phase === 'PROCESSING'
                ? `Processing: ${engine.status.currentStep || 'source file'}`
                : 'Watching for files'
              : engine.loading
                ? 'Checking status…'
                : 'Stopped'}
          </p>
          {engine.status?.currentFile && <p>Current file: {engine.status.currentFile}</p>}
          {engine.status && (
            <p>
              {engine.status.successfulFiles} files completed · {engine.status.failedFiles} failed
              in the latest run
            </p>
          )}
        </div>
        <div>
          <button onClick={() => openFlow()}>Open workflow</button>
          {engine.status?.running && (
            <button className="danger" disabled={engine.busy} onClick={engine.stop}>
              {engine.busy ? 'Stopping…' : 'Stop'}
            </button>
          )}
        </div>
      </section>
      {(engine.statusError || engine.error) && (
        <div className="config-notice error" role="status">
          {engine.statusError || engine.error}
          <button onClick={engine.retry}>Retry</button>
        </div>
      )}
      <WorkflowSourceSettings status={engine.status} />
      <p className="workflow-activity-help">
        Activity updates automatically. Recent history is retained while the backend is running;
        rolling server logs keep a longer trace.
      </p>
      <WorkflowActivity engine={engine} workflow={workflow} onReveal={openFlow} />
    </>
  )
}
