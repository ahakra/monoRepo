import { useEffect, useState } from 'react'
import { fieldsApi } from '../configuration/fieldsApi.js'
import { mappersApi as api } from './mappersApi.js'
import { validateOutputNames } from './mapperValidation.js'
import '../configuration/Configuration.css'
import './Mappers.css'

const newOutput = (output) => ({
  rowId: crypto.randomUUID(),
  name: output?.name || '',
  fieldDefinitionId: output?.fieldDefinitionId || '',
})

export default function Mappers() {
  const [items, setItems] = useState([])
  const [definitions, setDefinitions] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [form, setForm] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState('')
  const [deleting, setDeleting] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([api(), fieldsApi()])
      .then(([mappers, fields]) => {
        if (active) {
          setItems(mappers)
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
      const [mappers, fields] = await Promise.all([api(), fieldsApi()])
      setItems(mappers)
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
            directory: item.directory,
            wasmFileName: item.wasmFileName,
            outputs: item.outputs.map(newOutput),
          }
        : { name: '', description: '', directory: '', wasmFileName: '', outputs: [newOutput()] },
    )
    setNotice(null)
    setDeleting(null)
  }

  function change(key, value) {
    setForm((current) => ({ ...current, [key]: value }))
    setNotice(null)
  }
  function changeOutput(rowId, key, value) {
    change(
      'outputs',
      form.outputs.map((output) => (output.rowId === rowId ? { ...output, [key]: value } : output)),
    )
  }

  async function save(event) {
    event.preventDefault()
    const invalid = validateOutputNames(form.outputs)
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
        outputs: draft.outputs.map(({ name, fieldDefinitionId }) => ({ name, fieldDefinitionId })),
      })
      setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
      setForm(null)
      setNotice({ success: true, message: 'Mapper saved.' })
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
      setNotice({ success: true, message: 'Mapper deleted.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  return (
    <section aria-labelledby="mappers-title">
      <header className="config-header">
        <div>
          <h2 id="mappers-title">Mappers</h2>
          <p>Configure a WASM module and the named lists it outputs.</p>
        </div>
        <button
          className="primary"
          disabled={!!form || !!busy || loading || loadError}
          onClick={() => open()}
        >
          + New mapper
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
            <h2>{form.id ? 'Edit mapper' : 'New mapper'}</h2>
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
                  Mapper name
                  <input
                    required
                    autoFocus
                    maxLength={120}
                    value={form.name}
                    onChange={(e) => change('name', e.target.value)}
                    placeholder="e.g. CDR parser"
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
                    placeholder="e.g. /opt/etl/mappers"
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
                    placeholder="e.g. cdr_parser.wasm"
                  />
                  <small>
                    Include the .wasm extension; enter the filename separately from its directory.
                  </small>
                </label>
              </div>
              <section className="mapper-outputs" aria-labelledby="mapper-outputs-title">
                <header className="mapper-outputs-header">
                  <div>
                    <h2 id="mapper-outputs-title">Output lists</h2>
                    <p>
                      Add one or more named lists and choose a Fields definition as the type for
                      each.
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={form.outputs.length >= 100}
                    onClick={() => change('outputs', [...form.outputs, newOutput()])}
                  >
                    + Add output list
                  </button>
                </header>
                <p className="mapper-help" id="output-names-help">
                  Names must match the module's output names exactly, including case. No spaces or
                  whitespace; each name must be unique within this mapper. Names are saved exactly
                  as entered.
                </p>
                {!definitions.length && (
                  <p className="mapper-help">
                    Create a <a href="#schemas-tables">Fields definition</a> before saving outputs.
                  </p>
                )}
                {form.outputs.map((output, index) => {
                  const selected = definitions.find(
                    (definition) => definition.id === output.fieldDefinitionId,
                  )
                  return (
                    <section
                      className="mapper-output"
                      key={output.rowId}
                      aria-label={`Output list ${index + 1}`}
                    >
                      <div className="config-fields">
                        <label>
                          Output list name
                          <input
                            required
                            maxLength={120}
                            pattern={'\\S+'}
                            aria-describedby="output-names-help"
                            value={output.name}
                            onChange={(e) => changeOutput(output.rowId, 'name', e.target.value)}
                            placeholder="e.g. CallRecords"
                          />
                        </label>
                        <label>
                          Fields type
                          <select
                            required
                            value={output.fieldDefinitionId}
                            onChange={(e) =>
                              changeOutput(output.rowId, 'fieldDefinitionId', e.target.value)
                            }
                          >
                            <option value="">Choose a Fields definition</option>
                            {definitions.map((definition) => (
                              <option key={definition.id} value={definition.id}>
                                {definition.name}
                              </option>
                            ))}
                          </select>
                        </label>
                      </div>
                      {selected && (
                        <div className="mapper-field-preview">
                          <strong>{selected.name}</strong>
                          <div className="field-summary">
                            {Object.entries(selected.fields).map(([key, type]) => (
                              <code key={key}>
                                {key}: {type}
                              </code>
                            ))}
                          </div>
                        </div>
                      )}
                      <button
                        type="button"
                        className="danger mapper-remove"
                        disabled={form.outputs.length === 1}
                        onClick={() =>
                          change(
                            'outputs',
                            form.outputs.filter((item) => item.rowId !== output.rowId),
                          )
                        }
                        aria-label={`Remove output list ${output.name || index + 1}`}
                      >
                        Remove list
                      </button>
                    </section>
                  )
                })}
              </section>
              <footer>
                <small>
                  Saving stores the mapper configuration and output types. Module execution is not
                  connected yet.
                </small>
                <button type="submit" className="primary" disabled={!definitions.length}>
                  {busy === 'save' ? 'Saving…' : 'Save mapper'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved mappers" aria-busy={loading}>
          <header>
            <h2>
              Saved mappers <b>{items.length}</b>
            </h2>
            <button disabled={loading || !!busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          {loading ? (
            <div className="config-empty">Loading mappers…</div>
          ) : loadError ? (
            <div className="config-empty">
              Could not load mappers and Fields. Use Refresh to try again.
            </div>
          ) : !items.length ? (
            <div className="config-empty">
              <h3>Create your first mapper</h3>
              <p>
                Set its WASM directory and filename, then define the output lists and their types.
              </p>
              <button className="primary" onClick={() => open()}>
                + New mapper
              </button>
            </div>
          ) : (
            items.map((item) => (
              <article className="config-row" key={item.id}>
                <div className="config-row-info">
                  <h3>{item.name}</h3>
                  {item.description && <p>{item.description}</p>}
                  <p>
                    Directory: <code>{item.directory}</code> · File:{' '}
                    <code>{item.wasmFileName}</code>
                  </p>
                  <div className="mapper-saved-outputs">
                    {item.outputs.map((output) => (
                      <div key={output.name}>
                        <p>
                          <code>{output.name}</code> →{' '}
                          {definitions.find(
                            (definition) => definition.id === output.fieldDefinitionId,
                          )?.name || 'Fields'}{' '}
                          <b>{Object.keys(output.fields).length} fields</b>
                        </p>
                        <p className="field-summary">
                          {Object.entries(output.fields).map(([key, type]) => (
                            <code key={key}>
                              {key}: {type}
                            </code>
                          ))}
                        </p>
                      </div>
                    ))}
                  </div>
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
            Delete mapper <strong>{deleting.name}</strong> and its output configuration?
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
