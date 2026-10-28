import { useEffect, useState } from 'react'
import { fieldsApi as api } from './fieldsApi.js'

const types = [
  'string',
  'integer',
  'long',
  'decimal',
  'boolean',
  'date',
  'datetime',
  'uuid',
  'json',
]
const baseFields = { id: 'uuid', datetime: 'datetime' }
const newRow = (key = '', type = 'string') => ({ rowId: crypto.randomUUID(), key, type })

export default function Fields({ active }) {
  const [items, setItems] = useState([])
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)
  const [deleting, setDeleting] = useState(null)

  useEffect(() => {
    if (!active) return
    let mounted = true
    api()
      .then((data) => {
        if (mounted) {
          setItems(data)
          setForm((current) =>
            current
              ? { ...current, usedBy: data.find((item) => item.id === current.id)?.usedBy || [] }
              : current,
          )
        }
      })
      .catch((error) => {
        if (mounted) setNotice({ success: false, message: error.message })
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [active])

  async function refresh() {
    setLoading(true)
    setNotice(null)
    try {
      setItems(await api())
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setLoading(false)
    }
  }

  function open(item) {
    setForm({
      id: item?.id,
      name: item?.name || '',
      usedBy: item?.usedBy || [],
      rows: Object.entries({ ...baseFields, ...item?.fields }).map(([key, type]) =>
        newRow(key, type),
      ),
    })
    setNotice(null)
    setDeleting(null)
  }

  function changeRow(rowId, property, value) {
    setForm({
      ...form,
      rows: form.rows.map((row) => (row.rowId === rowId ? { ...row, [property]: value } : row)),
    })
    setNotice(null)
  }

  async function save(event) {
    event.preventDefault()
    const keys = form.rows.map((row) => row.key.trim())
    if (new Set(keys).size !== keys.length) {
      setNotice({
        success: false,
        message: 'Each field key must be unique, including id and datetime.',
      })
      return
    }
    setBusy(true)
    setNotice(null)
    try {
      const saved = await api(form.id ? `/${form.id}` : '', form.id ? 'PUT' : 'POST', {
        name: form.name.trim(),
        fields: Object.fromEntries(form.rows.map((row) => [row.key.trim(), row.type])),
      })
      setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
      setForm(null)
      setNotice({ success: true, message: 'Fields saved.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    setBusy(true)
    setNotice(null)
    try {
      await api(`/${deleting.id}`, 'DELETE')
      setItems((current) => current.filter((item) => item.id !== deleting.id))
      setDeleting(null)
      setNotice({ success: true, message: 'Fields deleted.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby="fields-title">
      <header className="config-header">
        <div>
          <h2 id="fields-title">Fields</h2>
          <p>
            Define reusable dictionaries of field keys and types for tables, rules, mappers, and
            transformers.
          </p>
        </div>
        <button className="primary" disabled={!!form || busy || loading} onClick={() => open()}>
          + New fields
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
            <div>
              <h2>{form.id ? 'Edit fields' : 'New fields'}</h2>
              <p>Every definition includes id (UUID) and datetime. Add your own keys below.</p>
            </div>
            <button
              disabled={busy}
              onClick={() => {
                setForm(null)
                setNotice(null)
              }}
            >
              Cancel
            </button>
          </header>
          <form onSubmit={save}>
            <fieldset disabled={busy}>
              <div className="config-fields">
                <label>
                  Definition name
                  <input
                    required
                    maxLength={120}
                    value={form.name}
                    onChange={(event) => setForm({ ...form, name: event.target.value })}
                    placeholder="e.g. Customer"
                    autoFocus
                  />
                </label>
              </div>
              <div className="field-editor">
                <p id="field-key-help">
                  Keys use lowercase letters, numbers, and underscores, start with a letter or
                  underscore, and have up to 63 characters.
                </p>
                {form.rows.map((row, index) => {
                  const usedBy = form.usedBy.filter((reference) =>
                    Object.hasOwn(reference.fields, row.key),
                  )
                  const locked = index < 2 || usedBy.length > 0
                  return (
                    <div className="field-editor-row" key={row.rowId}>
                      <label>
                        Field key
                        <input
                          aria-describedby="field-key-help"
                          required
                          pattern="[a-z_][a-z0-9_]{0,62}"
                          maxLength={63}
                          value={row.key}
                          readOnly={locked}
                          onChange={(event) => changeRow(row.rowId, 'key', event.target.value)}
                          placeholder="e.g. lastname"
                        />
                      </label>
                      <label>
                        Type
                        <select
                          value={row.type}
                          disabled={locked}
                          onChange={(event) => changeRow(row.rowId, 'type', event.target.value)}
                        >
                          {types.map((type) => (
                            <option key={type} value={type}>
                              {type === 'uuid' ? 'UUID' : type === 'json' ? 'JSON' : type}
                            </option>
                          ))}
                        </select>
                      </label>
                      {locked ? (
                        <span
                          className="config-badge"
                          title={
                            usedBy.length
                              ? `Used by: ${usedBy.map((reference) => reference.tableName).join(', ')}. Update the dependent tables, rules, mappers, or transformers before editing this field.`
                              : 'Built-in field'
                          }
                        >
                          {usedBy.length ? 'In use' : 'Default'}
                        </span>
                      ) : (
                        <button
                          type="button"
                          className="danger"
                          aria-label={`Remove field ${row.key || index + 1}`}
                          onClick={() =>
                            setForm({
                              ...form,
                              rows: form.rows.filter((item) => item.rowId !== row.rowId),
                            })
                          }
                        >
                          Remove
                        </button>
                      )}
                    </div>
                  )
                })}
                {form.usedBy.length > 0 && (
                  <p className="field-usage-help">
                    Locked fields are used by:{' '}
                    {form.usedBy.map((reference) => reference.tableName).join(', ')}. Update the
                    dependent tables, rules, mappers, or transformers before removing these fields
                    or changing their types.
                  </p>
                )}
                <button
                  type="button"
                  disabled={form.rows.length >= 100}
                  onClick={() => setForm({ ...form, rows: [...form.rows, newRow()] })}
                >
                  + Add field
                </button>
                <small>{form.rows.length} / 100 fields</small>
              </div>
              <footer>
                <small>
                  Saving stores a reusable definition for Tables, Rules, Mappers, and Transformers.
                  Referenced fields must keep compatible keys and types.
                </small>
                <button type="submit" className="primary">
                  {busy ? 'Saving…' : 'Save fields'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved fields" aria-busy={loading}>
          <header>
            <h2>
              Saved fields <b>{items.length}</b>
            </h2>
            <button disabled={loading || busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          {loading ? (
            <div className="config-empty">Loading fields…</div>
          ) : items.length === 0 ? (
            <div className="config-empty">
              <div className="empty-icon" aria-hidden="true">
                {'{ }'}
              </div>
              <h3>Define your first set of fields</h3>
              <p>
                Start with id and datetime, then add keys like name and lastname with their types.
              </p>
              <button className="primary" onClick={() => open()}>
                + Add fields
              </button>
            </div>
          ) : (
            items.map((item) => (
              <article className="config-row" key={item.id}>
                <div className="database-icon" aria-hidden="true">
                  {'{ }'}
                </div>
                <div className="config-row-info">
                  <h3>
                    {item.name} <b>{Object.keys(item.fields).length} fields</b>
                  </h3>
                  <p className="field-summary">
                    {Object.entries(item.fields).map(([key, type]) => (
                      <code key={key}>
                        {key}: {type}
                      </code>
                    ))}
                  </p>
                  <p>
                    Updated{' '}
                    <time dateTime={item.updatedAt}>
                      {new Date(item.updatedAt).toLocaleString()}
                    </time>
                  </p>
                  {item.usedBy?.length > 0 && (
                    <p>Used by: {item.usedBy.map((reference) => reference.tableName).join(', ')}</p>
                  )}
                </div>
                <button disabled={busy} onClick={() => open(item)} aria-label={`Edit ${item.name}`}>
                  Edit
                </button>
                <button
                  className="danger"
                  disabled={busy || item.usedBy?.length > 0}
                  title={
                    item.usedBy?.length
                      ? `Used by ${item.usedBy.map((reference) => reference.tableName).join(', ')}`
                      : undefined
                  }
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
            Delete <strong>{deleting.name}</strong>? This removes the saved field definition.
          </p>
          <button disabled={busy} onClick={() => setDeleting(null)}>
            Cancel
          </button>
          <button disabled={busy} onClick={remove}>
            {busy ? 'Deleting…' : 'Confirm delete'}
          </button>
        </div>
      )}
    </section>
  )
}
