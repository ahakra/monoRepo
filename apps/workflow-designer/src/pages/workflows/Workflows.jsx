import { useEffect, useState } from 'react'
import { ReactFlowProvider } from '@xyflow/react'
import {
  ItemFilters,
  ItemTimestamps,
  NoMatchingItems,
} from '../../components/items/ItemFilters.jsx'
import { useItemFilters } from '../../components/items/useItemFilters.js'
import { createWorkflow, loadWorkflows, serializeWorkflow } from './workflowModel.js'
import WorkflowEditor from './WorkflowEditor.jsx'
import { workflowsApi } from './workflowsApi.js'
import { BlockIcon } from './WorkflowNodes.jsx'
import '@xyflow/react/dist/style.css'
import '../configuration/Configuration.css'
import './Workflows.css'

const filterConfig = {
  searchFields: [
    { id: 'name', label: 'Name', values: (item) => item.name },
    { id: 'description', label: 'Description', values: (item) => item.description },
  ],
}

export default function Workflows() {
  const [legacy] = useState(() => loadWorkflows())
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [draft, setDraft] = useState(null)
  const [notice, setNotice] = useState('')
  const [deleting, setDeleting] = useState(null)
  const filtered = useItemFilters(items, filterConfig)

  useEffect(() => {
    let active = true
    workflowsApi()
      .then((workflows) => {
        if (active)
          setItems(workflows.map((item) => serializeWorkflow(item, { checkTypes: false })))
      })
      .catch((error) => {
        if (active) setLoadError(error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [reload])

  function refresh() {
    setLoading(true)
    setLoadError('')
    setReload((value) => value + 1)
  }

  async function save(workflow) {
    const definition = serializeWorkflow(workflow)
    const exists = items.some((item) => item.id === definition.id)
    const saved = await workflowsApi(
      exists ? `/${definition.id}` : '',
      exists ? 'PUT' : 'POST',
      definition,
    )
    setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
    return saved
  }
  async function remove() {
    setBusy(true)
    try {
      await workflowsApi(`/${deleting.id}`, 'DELETE')
      setItems((current) => current.filter((item) => item.id !== deleting.id))
      setDeleting(null)
      setNotice('')
    } catch (error) {
      setNotice(error.message)
    } finally {
      setBusy(false)
    }
  }
  async function importBrowserDrafts() {
    setBusy(true)
    setNotice('')
    try {
      // Read server IDs again so retrying a partially completed import never overwrites drafts.
      const existing = await workflowsApi()
      const ids = new Set(existing.map((item) => item.id))
      for (const workflow of legacy.workflows) {
        if (ids.has(workflow.id)) continue
        await save(workflow)
      }
      refresh()
    } catch (error) {
      setNotice(`Import stopped: ${error.message} Browser copies are still available.`)
    } finally {
      setBusy(false)
    }
  }
  const importCount = legacy.workflows.filter(
    (item) => !items.some((saved) => saved.id === item.id),
  ).length

  if (draft)
    return (
      <ReactFlowProvider key={draft.id}>
        <WorkflowEditor
          draft={draft}
          isNew={!items.some((item) => item.id === draft.id)}
          onSave={save}
          onBack={() => setDraft(null)}
        />
      </ReactFlowProvider>
    )
  return (
    <main className="page config workflows-page" aria-labelledby="workflows-title">
      <div className="config-eyebrow">WORKSPACE / WORKFLOWS</div>
      <header className="config-header">
        <div>
          <h1 id="workflows-title">Workflows</h1>
          <p>Start with a data source. Shape the path your data will take.</p>
        </div>
        <button
          className="primary"
          disabled={loading || !!loadError || busy}
          onClick={() => setDraft(createWorkflow())}
        >
          + New workflow
        </button>
      </header>
      <div className="workflow-intro">
        <span className="workflow-intro-icon">
          <BlockIcon source />
        </span>
        <div>
          <h2>Every flow begins with a source</h2>
          <p>
            Arrange blocks, connect arrows, and choose saved connectors. Workflow drafts are stored
            in the database.
          </p>
        </div>
        <span className="workflow-draft-badge">Design preview</span>
      </div>
      {(loadError || notice || legacy.error) && (
        <div className="config-notice error" role="status">
          {notice || loadError || legacy.error}
          {loadError && <button onClick={refresh}>Retry loading</button>}
        </div>
      )}
      {!!importCount && (
        <div className="config-notice" role="status">
          {importCount} browser draft(s) available to import. Browser copies will be kept.
          <button disabled={loading || !!loadError || busy} onClick={importBrowserDrafts}>
            {busy ? 'Working…' : 'Import browser drafts'}
          </button>
          <details>
            <summary>Review browser drafts</summary>
            {legacy.workflows
              .filter((item) => !items.some((saved) => saved.id === item.id))
              .map((item) => (
                <p key={item.id}>
                  {item.name}{' '}
                  <button disabled={loading || busy} onClick={() => setDraft(item)}>
                    Open browser draft
                  </button>
                </p>
              ))}
          </details>
        </div>
      )}
      <section className="config-panel" aria-label="Workflow drafts">
        <header>
          <h2>
            Workflow drafts <b>{items.length}</b>
          </h2>
          <span className="workflow-storage-label">Saved in database</span>
        </header>
        <ItemFilters filter={filtered} label="workflows" />
        {loading ? (
          <p role="status">Loading workflows…</p>
        ) : loadError ? (
          <p>Workflow drafts could not be loaded.</p>
        ) : !items.length ? (
          <div className="config-empty">
            <div className="workflow-empty-icon">
              <BlockIcon source />
            </div>
            <h3>Design your first workflow</h3>
            <p>
              A Data source block is added automatically. Drag it, configure it, and connect a
              next-step placeholder.
            </p>
            <button
              className="primary"
              disabled={loading || !!loadError || busy}
              onClick={() => setDraft(createWorkflow())}
            >
              + New workflow
            </button>
          </div>
        ) : !filtered.items.length ? (
          <NoMatchingItems filter={filtered} />
        ) : (
          filtered.items.map((item) => (
            <article className="config-row" key={item.id}>
              <div className="config-row-info">
                <h3>{item.name}</h3>
                {item.description && <p>{item.description}</p>}
                <ItemTimestamps item={item} />
                <p>
                  {item.nodes.length} blocks · {item.edges.length} connections ·{' '}
                  {item.nodes.find((node) => node.type === 'dataSource')?.data.connectorName ||
                    'Data source not configured'}
                </p>
              </div>
              <button
                disabled={busy}
                onClick={() => setDraft(item)}
                aria-label={`Open ${item.name}`}
              >
                Open editor
              </button>
              <button
                className="danger"
                disabled={busy}
                onClick={() => setDeleting(item)}
                aria-label={`Delete ${item.name}`}
              >
                Delete
              </button>
            </article>
          ))
        )}
      </section>
      {deleting && (
        <div className="config-confirm" role="alert">
          <p>
            Delete workflow draft <strong>{deleting.name}</strong>?
          </p>
          <button disabled={busy} onClick={() => setDeleting(null)}>
            Cancel
          </button>
          <button disabled={busy} onClick={remove}>
            Confirm delete
          </button>
        </div>
      )}
    </main>
  )
}
