import { useEffect, useState } from 'react'
import { connectionApi } from './connectionApi.js'
import { fieldsApi } from './fieldsApi.js'
import { tablesApi as api } from './tablesApi.js'
import SchemaChanges from './SchemaChanges.jsx'

async function loadConfiguration() {
  const [tables, fields, connections] = await Promise.all([api(), fieldsApi(), connectionApi()])
  return {
    tables,
    fields,
    connections: connections.filter((connection) => connection.type === 'postgres'),
  }
}

export default function Tables({ active }) {
  const [data, setData] = useState({ tables: [], fields: [], connections: [] })
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [form, setForm] = useState(null)
  const [adding, setAdding] = useState('')
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)
  const [confirmation, setConfirmation] = useState(null)
  const [reviewing, setReviewing] = useState(null)

  useEffect(() => {
    if (!active) return
    let current = true
    loadConfiguration()
      .then((result) => {
        if (current) {
          setData(result)
          setLoadError(false)
        }
      })
      .catch((error) => {
        if (current) {
          setLoadError(true)
          setNotice({ success: false, message: error.message })
        }
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [active])

  async function refresh() {
    setLoading(true)
    setNotice(null)
    try {
      setData(await loadConfiguration())
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
            connectionId: item.connectionId,
            fieldDefinitionId: item.fieldDefinitionId,
            schemaName: item.schemaName,
            tableName: item.tableName,
            fields: Object.keys(item.columns),
          }
        : {
            name: '',
            connectionId: '',
            fieldDefinitionId: '',
            schemaName: 'public',
            tableName: '',
            fields: [],
          },
    )
    setAdding('')
    setReviewing(null)
    setConfirmation(null)
    setNotice(null)
  }

  function change(event) {
    const { name, value } = event.target
    setForm({ ...form, [name]: value, ...(name === 'fieldDefinitionId' ? { fields: [] } : {}) })
    if (name === 'fieldDefinitionId') setAdding('')
    setNotice(null)
  }

  async function save(event) {
    event.preventDefault()
    setBusy('save')
    setNotice(null)
    try {
      const saved = await api(form.id ? `/${form.id}` : '', form.id ? 'PUT' : 'POST', form)
      setData((current) => ({
        ...current,
        tables: [saved, ...current.tables.filter((item) => item.id !== saved.id)],
      }))
      setForm(null)
      setNotice({
        success: true,
        message:
          'Table configuration saved. Create the PostgreSQL table or review column changes to update an existing table.',
      })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  async function confirm() {
    const { item, action } = confirmation
    setBusy(action)
    setNotice(null)
    try {
      if (action === 'delete') {
        await api(`/${item.id}`, 'DELETE')
        setData((current) => ({
          ...current,
          tables: current.tables.filter((table) => table.id !== item.id),
        }))
        setNotice({
          success: true,
          message: 'Table configuration deleted. The PostgreSQL table was not changed.',
        })
      } else {
        const result = await api(`/${item.id}/create`, 'POST')
        setNotice({ success: true, message: result.message })
      }
      setConfirmation(null)
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  const dictionary = data.fields.find((item) => item.id === form?.fieldDefinitionId)
  const available = Object.entries(dictionary?.fields || {}).filter(
    ([key]) => !form?.fields.includes(key),
  )
  const missingFields = form?.fields.some((key) => !Object.hasOwn(dictionary?.fields || {}, key))
  const ready = data.fields.length > 0 && data.connections.length > 0 && !loading && !loadError
  const connectionName = (id) =>
    data.connections.find((item) => item.id === id)?.name || 'Unavailable connection'
  const fieldsName = (id) =>
    data.fields.find((item) => item.id === id)?.name || 'Unavailable Fields definition'

  return (
    <section aria-labelledby="tables-title">
      <header className="config-header">
        <div>
          <h2 id="tables-title">Tables</h2>
          <p>Map fields to a PostgreSQL table using an existing connection.</p>
        </div>
        <button className="primary" disabled={!!form || !!busy || !ready} onClick={() => open()}>
          + New table
        </button>
      </header>
      {notice && (
        <div className={`config-notice ${notice.success ? 'success' : 'error'}`} role="status">
          {notice.message}
        </div>
      )}
      {!loading && !loadError && !ready && (
        <div className="config-notice" role="status">
          Create a PostgreSQL connection in Connections and a definition in Fields before adding a
          table.
        </div>
      )}
      {form ? (
        <section className="config-panel">
          <header>
            <div>
              <h2>{form.id ? 'Edit table' : 'New table'}</h2>
              <p>
                Choose a Fields definition, then add all its fields or select them individually.
              </p>
            </div>
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
            <fieldset disabled={!!busy || loading || loadError}>
              <div className="config-fields">
                <label>
                  Configuration name
                  <input
                    name="name"
                    value={form.name}
                    onChange={change}
                    required
                    maxLength={120}
                    placeholder="e.g. CDR destination"
                    autoFocus
                  />
                </label>
                <label>
                  PostgreSQL connection
                  <select name="connectionId" value={form.connectionId} onChange={change} required>
                    <option value="">Select a PostgreSQL connection</option>
                    {data.connections.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name} — {item.database}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Schema name
                  <input
                    name="schemaName"
                    value={form.schemaName}
                    onChange={change}
                    required
                    pattern="[a-z_][a-z0-9_]{0,62}"
                    maxLength={63}
                    placeholder="public"
                  />
                  <small>The schema must already exist in the selected database.</small>
                </label>
                <label>
                  Table name
                  <input
                    name="tableName"
                    value={form.tableName}
                    onChange={change}
                    required
                    pattern="[a-z_][a-z0-9_]{0,62}"
                    maxLength={63}
                    placeholder="cdr_records"
                  />
                  <small>
                    Use lowercase letters, numbers, and underscores; start with a letter or
                    underscore.
                  </small>
                </label>
                <label>
                  Fields definition
                  <select
                    name="fieldDefinitionId"
                    value={form.fieldDefinitionId}
                    onChange={change}
                    required
                  >
                    <option value="">Select a Fields definition</option>
                    {data.fields.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name} ({Object.keys(item.fields).length} fields)
                      </option>
                    ))}
                  </select>
                  <small>Changing the definition clears the selected fields.</small>
                </label>
              </div>
              <div className="table-field-picker">
                <div className="table-picker-header">
                  <h3>
                    Table fields <b>{form.fields.length}</b>
                  </h3>
                  <div>
                    <button
                      type="button"
                      disabled={!dictionary || available.length === 0}
                      onClick={() => {
                        setForm({ ...form, fields: Object.keys(dictionary.fields) })
                        setAdding('')
                      }}
                    >
                      Add all fields
                    </button>
                    <button
                      type="button"
                      disabled={form.fields.length === 0}
                      onClick={() => {
                        setForm({ ...form, fields: [] })
                        setAdding('')
                      }}
                    >
                      Remove all
                    </button>
                  </div>
                </div>
                <div className="table-add-field">
                  <label>
                    Add one field
                    <select
                      value={adding}
                      onChange={(event) => setAdding(event.target.value)}
                      disabled={!dictionary || available.length === 0}
                    >
                      <option value="">
                        {dictionary
                          ? available.length
                            ? 'Select a field'
                            : 'All fields added'
                          : 'Choose a Fields definition first'}
                      </option>
                      {available.map(([key, type]) => (
                        <option key={key} value={key}>
                          {key} — {type}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    disabled={!adding || !available.some(([key]) => key === adding)}
                    onClick={() => {
                      setForm({ ...form, fields: [...form.fields, adding] })
                      setAdding('')
                    }}
                  >
                    + Add field
                  </button>
                </div>
                {missingFields && (
                  <p role="alert">
                    Some selected fields are no longer available. Remove them or choose the Fields
                    definition again.
                  </p>
                )}
                {form.fields.length === 0 ? (
                  <p className="table-picker-empty">
                    No fields selected. Add at least one field to save the table.
                  </p>
                ) : (
                  <ul className="table-selected-fields">
                    {form.fields.map((key) => (
                      <li key={key}>
                        <code>{key}</code>
                        <span className="config-badge">
                          {dictionary?.fields[key] || 'Unavailable'}
                        </span>
                        <button
                          type="button"
                          className="danger"
                          aria-label={`Remove ${key} from table`}
                          onClick={() =>
                            setForm({
                              ...form,
                              fields: form.fields.filter((field) => field !== key),
                            })
                          }
                        >
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <small>
                  Keys and types come from the selected Fields definition. Only selected fields
                  become columns, including id and datetime if you add them.
                </small>
              </div>
              <footer>
                <small>
                  Saving stores configuration. Create the PostgreSQL table from the saved list.
                </small>
                <button
                  className="primary"
                  type="submit"
                  disabled={form.fields.length === 0 || missingFields}
                >
                  {busy === 'save' ? 'Saving…' : 'Save table'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved tables" aria-busy={loading}>
          <header>
            <h2>
              Saved tables <b>{data.tables.length}</b>
            </h2>
            <button disabled={loading || !!busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          {loading ? (
            <div className="config-empty">Loading tables…</div>
          ) : loadError ? (
            <div className="config-empty">
              Could not load table configuration. Use Refresh to try again.
            </div>
          ) : data.tables.length === 0 ? (
            <div className="config-empty">
              <div className="empty-icon" aria-hidden="true">
                ▦
              </div>
              <h3>Map your first table</h3>
              <p>Choose your CDR fields or another definition and a PostgreSQL connection.</p>
              <button className="primary" disabled={!ready} onClick={() => open()}>
                + Add table
              </button>
            </div>
          ) : (
            data.tables.map((item) => (
              <article className="saved-table" key={item.id}>
                <div className="config-row">
                  <div className="database-icon" aria-hidden="true">
                    ▦
                  </div>
                  <div className="config-row-info">
                    <h3>
                      {item.name} <b>{Object.keys(item.columns).length} fields</b>
                    </h3>
                    <p>
                      <code>
                        {item.schemaName}.{item.tableName}
                      </code>{' '}
                      · {connectionName(item.connectionId)}
                    </p>
                    <p>Fields: {fieldsName(item.fieldDefinitionId)}</p>
                  </div>
                  <div className="table-row-actions">
                    <button
                      disabled={!!busy}
                      onClick={() => open(item)}
                      aria-label={`Edit ${item.name}`}
                    >
                      Edit
                    </button>
                    <button
                      disabled={!!busy || reviewing?.id === item.id}
                      onClick={() => {
                        setReviewing(item)
                        setConfirmation(null)
                        setNotice(null)
                        setBusy('schema-review')
                      }}
                    >
                      Review column changes
                    </button>
                    <button
                      className="primary"
                      disabled={!!busy}
                      onClick={() => {
                        setConfirmation({ item, action: 'create' })
                        setReviewing(null)
                        setNotice(null)
                      }}
                    >
                      Create PostgreSQL table
                    </button>
                    <button
                      className="danger"
                      disabled={!!busy}
                      onClick={() => {
                        setConfirmation({ item, action: 'delete' })
                        setReviewing(null)
                        setNotice(null)
                      }}
                      aria-label={`Delete ${item.name}`}
                    >
                      Delete
                    </button>
                  </div>
                </div>
                <details className="table-sql">
                  <summary>View CREATE TABLE SQL</summary>
                  <pre>{item.sql}</pre>
                </details>
              </article>
            ))
          )}
        </section>
      )}
      {reviewing && (
        <SchemaChanges
          key={reviewing.id}
          item={reviewing}
          onClose={() => setReviewing(null)}
          onNotice={setNotice}
          onBusy={setBusy}
        />
      )}
      {confirmation && (
        <div className="config-confirm" role="alert">
          <p>
            {confirmation.action === 'create' ? (
              <>
                Create{' '}
                <strong>
                  {confirmation.item.schemaName}.{confirmation.item.tableName}
                </strong>{' '}
                on <strong>{connectionName(confirmation.item.connectionId)}</strong>? Existing
                tables will not be modified.
              </>
            ) : (
              <>
                Delete configuration <strong>{confirmation.item.name}</strong>? This keeps any
                existing PostgreSQL table.
              </>
            )}
          </p>
          {confirmation.action === 'create' && (
            <pre className="table-confirm-sql">{confirmation.item.sql}</pre>
          )}
          <button disabled={!!busy} onClick={() => setConfirmation(null)}>
            Cancel
          </button>
          <button
            className={confirmation.action === 'create' ? 'primary' : 'danger'}
            disabled={!!busy}
            onClick={confirm}
          >
            {busy
              ? 'Working…'
              : confirmation.action === 'create'
                ? 'Create table'
                : 'Confirm delete'}
          </button>
        </div>
      )}
      <p className="config-footnote">
        Save mappings first, then review and apply PostgreSQL column changes separately. Field types
        are inherited from Fields. New columns have no automatic defaults, keys, or indexes.
      </p>
    </section>
  )
}
