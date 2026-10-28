import { useItemFilters } from '../../components/items/useItemFilters.js'
import {
  ItemFilters,
  ItemTimestamps,
  NoMatchingItems,
} from '../../components/items/ItemFilters.jsx'
import { transformerFilters } from '../../components/items/itemFilterConfigs.js'
import { useEffect, useState } from 'react'
import { fieldsApi } from '../configuration/fieldsApi.js'
import { transformersApi as api } from './transformersApi.js'
import { newPort, serializePorts, validatePorts } from './transformerPorts.js'
import { TransformerPortsEditor, TransformerPortsSummary } from './TransformerPorts.jsx'

export default function Transformers() {
  const [items, setItems] = useState([])
  const [definitions, setDefinitions] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)
  const [deleting, setDeleting] = useState(null)

  const filtered = useItemFilters(items, transformerFilters(definitions))

  useEffect(() => {
    let active = true
    Promise.all([api(), fieldsApi()])
      .then(([transformers, fields]) => {
        if (active) {
          setItems(transformers)
          setDefinitions(fields)
        }
      })
      .catch((error) => {
        if (active) {
          setLoadError(true)
          setNotice({ success: false, message: error.message })
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  async function refresh() {
    setLoading(true)
    setNotice(null)
    try {
      const [transformers, fields] = await Promise.all([api(), fieldsApi()])
      setItems(transformers)
      setDefinitions(fields)
      setLoadError(false)
    } catch (error) {
      setLoadError(true)
      setNotice({ success: false, message: error.message })
    } finally {
      setLoading(false)
    }
  }

  function open(item) {
    setForm(
      item
        ? {
            id: item.id,
            name: item.name,
            description: item.description,
            directory: item.directory || '',
            wasmFileName: item.wasmFileName || '',
            inputs: item.inputs.map(newPort),
            outputs: item.outputs.map(newPort),
          }
        : {
            name: '',
            description: '',
            directory: '',
            wasmFileName: '',
            inputs: [newPort()],
            outputs: [newPort()],
          },
    )
    setNotice(null)
    setDeleting(null)
  }
  function change(key, value) {
    setForm((current) => ({ ...current, [key]: value }))
    setNotice(null)
  }

  async function save(event) {
    event.preventDefault()
    const invalid = validatePorts(form.inputs, 'input') || validatePorts(form.outputs, 'output')
    if (invalid) {
      setNotice({ success: false, message: invalid })
      return
    }
    setBusy('save')
    setNotice(null)
    try {
      const { id, ...draft } = form
      const saved = await api(id ? `/${id}` : '', id ? 'PUT' : 'POST', {
        ...draft,
        inputs: serializePorts(draft.inputs),
        outputs: serializePorts(draft.outputs),
      })
      setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
      setForm(null)
      setNotice({ success: true, message: 'Transformer saved.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }
  async function remove() {
    setBusy('delete')
    setNotice(null)
    try {
      await api(`/${deleting.id}`, 'DELETE')
      setItems((current) => current.filter((item) => item.id !== deleting.id))
      setDeleting(null)
      setNotice({ success: true, message: 'Transformer deleted.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  return (
    <section aria-labelledby="transformers-title">
      <header className="config-header">
        <div>
          <h2 id="transformers-title">Transformers</h2>
          <p>Define multiple inputs and outputs as Fields lists or dynamic variables.</p>
        </div>
        <button
          className="primary"
          disabled={!!form || !!busy || loading || loadError}
          onClick={() => open()}
        >
          + New transformer
        </button>
      </header>
      {notice && (
        <div className={`config-notice ${notice.success ? 'success' : 'error'}`} role="status">
          {notice.message}
        </div>
      )}
      {form ? (
        <section className="config-panel">
          <header>
            <h2>{form.id ? 'Edit transformer' : 'New transformer'}</h2>
            <button
              disabled={!!busy}
              onClick={() => {
                setForm(null)
                setNotice(null)
              }}
            >
              Cancel
            </button>
          </header>
          <form onSubmit={save}>
            <fieldset disabled={!!busy}>
              <div className="config-fields">
                <label>
                  Transformer name
                  <input
                    required
                    autoFocus
                    maxLength={120}
                    value={form.name}
                    onChange={(e) => change('name', e.target.value)}
                    placeholder="e.g. Enrich call records"
                  />
                </label>
                <label>
                  Description
                  <textarea
                    rows={2}
                    maxLength={2000}
                    value={form.description}
                    onChange={(e) => change('description', e.target.value)}
                  />
                </label>
                <label>
                  WASM directory
                  <input
                    required
                    maxLength={2048}
                    value={form.directory}
                    onChange={(e) => change('directory', e.target.value)}
                    placeholder="e.g. /opt/etl/transformers"
                  />
                  <small>
                    Directory where the WASM module will be available to the Java service.
                  </small>
                </label>
                <label>
                  WASM filename
                  <input
                    required
                    maxLength={255}
                    value={form.wasmFileName}
                    onChange={(e) => change('wasmFileName', e.target.value)}
                    placeholder="e.g. enrich_calls.wasm"
                  />
                  <small>
                    Include the .wasm extension; enter the filename separately from its directory.
                  </small>
                </label>
              </div>
              <TransformerPortsEditor
                direction="input"
                ports={form.inputs}
                definitions={definitions}
                onChange={(value) => change('inputs', value)}
              />
              <TransformerPortsEditor
                direction="output"
                ports={form.outputs}
                definitions={definitions}
                onChange={(value) => change('outputs', value)}
              />
              <footer>
                <small>
                  Saving stores the WASM location, inputs, and outputs. Transformer evaluation will
                  be added later.
                </small>
                <button type="submit" className="primary">
                  {busy === 'save' ? 'Saving…' : 'Save transformer'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved transformers" aria-busy={loading}>
          <header>
            <h2>
              Saved transformers <b>{items.length}</b>
            </h2>
            <button disabled={loading || !!busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          <ItemFilters filter={filtered} label="transformers" disabled={loading} />
          {loading ? (
            <div className="config-empty">Loading transformers…</div>
          ) : loadError ? (
            <div className="config-empty">
              Could not load transformers and Fields. Use Refresh to try again.
            </div>
          ) : !items.length ? (
            <div className="config-empty">
              <h3>Create your first transformer</h3>
              <p>Combine named Fields lists and dynamic variables as inputs and outputs.</p>
              <button className="primary" onClick={() => open()}>
                + New transformer
              </button>
            </div>
          ) : !filtered.items.length ? (
            <NoMatchingItems filter={filtered} />
          ) : (
            filtered.items.map((item) => (
              <article className="config-row" key={item.id}>
                <div className="config-row-info">
                  <h3>{item.name}</h3>
                  <ItemTimestamps item={item} />
                  {item.description && <p>{item.description}</p>}
                  <p>
                    Directory: <code>{item.directory || 'Not configured'}</code> · File:{' '}
                    <code>{item.wasmFileName || 'Not configured'}</code>
                  </p>
                  <TransformerPortsSummary
                    title="Inputs"
                    ports={item.inputs}
                    definitions={definitions}
                  />
                  <TransformerPortsSummary
                    title="Outputs"
                    ports={item.outputs}
                    definitions={definitions}
                  />
                </div>
                <button
                  disabled={!!busy}
                  onClick={() => open(item)}
                  aria-label={`Edit ${item.name}`}
                >
                  Edit
                </button>
                <button
                  className="danger"
                  disabled={!!busy}
                  onClick={() => {
                    setDeleting(item)
                    setNotice(null)
                  }}
                  aria-label={`Delete ${item.name}`}
                >
                  Delete
                </button>
              </article>
            ))
          )}
        </section>
      )}
      {deleting && (
        <div className="config-confirm" role="alert">
          <p>
            Delete transformer <strong>{deleting.name}</strong> and its input/output configuration?
          </p>
          <button disabled={!!busy} onClick={() => setDeleting(null)}>
            Cancel
          </button>
          <button disabled={!!busy} onClick={remove}>
            {busy === 'delete' ? 'Deleting…' : 'Confirm delete'}
          </button>
        </div>
      )}
    </section>
  )
}
