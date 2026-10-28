import { useState } from 'react'
import { ReactFlowProvider } from '@xyflow/react'
import {
  ItemFilters,
  ItemTimestamps,
  NoMatchingItems,
} from '../../components/items/ItemFilters.jsx'
import { useItemFilters } from '../../components/items/useItemFilters.js'
import { createWorkflow, loadWorkflows, saveWorkflows, serializeWorkflow } from './workflowModel.js'
import WorkflowEditor from './WorkflowEditor.jsx'
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
  const [initial] = useState(() => loadWorkflows())
  const [items, setItems] = useState(initial.workflows)
  const [draft, setDraft] = useState(null)
  const [notice, setNotice] = useState('')
  const [deleting, setDeleting] = useState(null)
  const filtered = useItemFilters(items, filterConfig)

  function persist(workflows) {
    if (initial.error) throw new Error(initial.error)
    // Keep other legacy drafts editable while validating the draft being saved.
    const saved = saveWorkflows(workflows, undefined, { checkTypes: false })
    setItems(saved)
    return saved
  }
  function save(workflow) {
    const saved = serializeWorkflow({ ...workflow, updatedAt: new Date().toISOString() })
    persist([saved, ...items.filter((item) => item.id !== saved.id)])
    return saved
  }
  function remove() {
    try {
      persist(items.filter((item) => item.id !== deleting.id))
      setDeleting(null)
      setNotice('')
    } catch (error) {
      setNotice(error.message)
    }
  }

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
        <button className="primary" onClick={() => setDraft(createWorkflow())}>
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
            Arrange blocks, connect arrows, and choose a saved connector. Drafts stay in this
            browser while we build the editor step by step.
          </p>
        </div>
        <span className="workflow-draft-badge">Design preview</span>
      </div>
      {(initial.error || notice) && (
        <div className="config-notice error" role="status">
          {notice || initial.error}
        </div>
      )}
      <section className="config-panel" aria-label="Workflow drafts">
        <header>
          <h2>
            Workflow drafts <b>{items.length}</b>
          </h2>
          <span className="workflow-storage-label">Saved in this browser</span>
        </header>
        <ItemFilters filter={filtered} label="workflows" />
        {!items.length ? (
          <div className="config-empty">
            <div className="workflow-empty-icon">
              <BlockIcon source />
            </div>
            <h3>Design your first workflow</h3>
            <p>
              A Data source block is added automatically. Drag it, configure it, and connect a
              next-step placeholder.
            </p>
            <button className="primary" onClick={() => setDraft(createWorkflow())}>
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
              <button onClick={() => setDraft(item)} aria-label={`Open ${item.name}`}>
                Open editor
              </button>
              <button
                className="danger"
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
            Delete local draft <strong>{deleting.name}</strong>?
          </p>
          <button onClick={() => setDeleting(null)}>Cancel</button>
          <button onClick={remove}>Confirm delete</button>
        </div>
      )}
    </main>
  )
}
