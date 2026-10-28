import { useItemFilters } from '../../components/items/useItemFilters.js'
import {
  ItemFilters,
  ItemTimestamps,
  NoMatchingItems,
} from '../../components/items/ItemFilters.jsx'
import { connectionFilters } from '../../components/items/itemFilterConfigs.js'
import { useEffect, useState } from 'react'
import { connectionApi as api } from './connectionApi.js'
import PostgresFields from './PostgresFields.jsx'
import FileFields from './FileFields.jsx'
import { newConnection } from './connectionSettings.js'
export default function Connections() {
  const [items, setItems] = useState([])
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)
  const [deleting, setDeleting] = useState(null)
  async function refresh() {
    setLoading(true)
    try {
      setItems(await api())
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setLoading(false)
    }
  }
  const filtered = useItemFilters(items, connectionFilters)

  useEffect(() => {
    let active = true
    api()
      .then((data) => {
        if (active) setItems(data)
      })
      .catch((error) => {
        if (active) setNotice({ success: false, message: error.message })
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])
  function open(item) {
    setForm(item ? { ...newConnection(item.type), ...item, password: '' } : newConnection())
    setNotice(null)
    setDeleting(null)
  }
  async function submit(event) {
    event.preventDefault()
    const action = event.nativeEvent.submitter?.value || 'save'
    setBusy(action)
    setNotice(null)
    try {
      const result = await api(
        `${form.id ? `/${form.id}` : ''}${action === 'test' ? '/test' : ''}`,
        action === 'test' || !form.id ? 'POST' : 'PUT',
        form,
      )
      if (action === 'test') setNotice(result)
      else {
        setForm(null)
        setNotice({ success: true, message: 'Connection saved.' })
        await refresh()
      }
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }
  async function remove() {
    setBusy('delete')
    try {
      await api(`/${deleting.id}`, 'DELETE')
      setDeleting(null)
      setNotice({ success: true, message: 'Connection deleted.' })
      await refresh()
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }
  function change(event) {
    if (event.target.name === 'type') {
      setForm({ ...newConnection(event.target.value), name: form.name })
      setNotice(null)
      return
    }
    setForm({
      ...form,
      [event.target.name]:
        event.target.type === 'checkbox' ? event.target.checked : event.target.value,
    })
    setNotice(null)
  }
  return (
    <section aria-labelledby="connections-title">
      <header className="config-header">
        <div>
          <h2 id="connections-title">Connections</h2>
          <p>Manage reusable connections for your ETL sources and destinations.</p>
        </div>
        <button className="primary" disabled={!!form || !!busy} onClick={() => open()}>
          + New connection
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
              <h2>{form.id ? 'Edit connection' : 'New connection'}</h2>
              <p>
                {form.type === 'file'
                  ? 'Configure Camel File directories and test directory access.'
                  : 'Configure PostgreSQL and test your connection.'}
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
          <form onSubmit={submit}>
            <fieldset disabled={!!busy}>
              <div className="config-fields">
                <label>
                  Connection name
                  <input
                    name="name"
                    value={form.name}
                    onChange={change}
                    required
                    maxLength={120}
                    placeholder="e.g. Analytics database"
                    autoFocus
                  />
                </label>
                <label>
                  Connection type
                  <select name="type" value={form.type} onChange={change} disabled={!!form.id}>
                    <option value="postgres">PostgreSQL</option>
                    <option value="file">File</option>
                  </select>
                  {form.id && <small>Create a new connector to use a different type.</small>}
                </label>
                {form.type === 'postgres' && <PostgresFields form={form} onChange={change} />}
                {form.type === 'file' && <FileFields form={form} onChange={change} />}
              </div>
              <footer>
                <small>Saving stores configuration only.</small>
                <div>
                  <button type="submit" value="test">
                    {busy === 'test'
                      ? 'Testing…'
                      : form.type === 'file'
                        ? 'Test directories'
                        : form.createDatabaseIfNotExists
                          ? 'Create & test'
                          : 'Test connection'}
                  </button>
                  <button className="primary" type="submit" value="save">
                    {busy === 'save' ? 'Saving…' : 'Save connection'}
                  </button>
                </div>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved connections" aria-busy={loading}>
          <header>
            <h2>
              Saved connections <b>{items.length}</b>
            </h2>
            <button
              onClick={() => {
                setNotice(null)
                refresh()
              }}
              disabled={loading || !!busy}
            >
              Refresh
            </button>
          </header>
          <ItemFilters filter={filtered} label="connections" disabled={loading} />
          {loading ? (
            <div className="config-empty">Loading connections…</div>
          ) : items.length === 0 ? (
            <div className="config-empty">
              <div className="empty-icon" aria-hidden="true">
                ▤
              </div>
              <h3>Your first connection starts here</h3>
              <p>Create a reusable connection for an ETL source or destination.</p>
              <button className="primary" onClick={() => open()}>
                + Add connection
              </button>
            </div>
          ) : !filtered.items.length ? (
            <NoMatchingItems filter={filtered} />
          ) : (
            filtered.items.map((item) => (
              <article className="config-row" key={item.id}>
                <div className="database-icon" aria-hidden="true">
                  ▤
                </div>
                <div className="config-row-info">
                  <h3>{item.name}</h3>
                  <ItemTimestamps item={item} />
                  {item.type === 'file' ? (
                    <>
                      <p>
                        Source: <code>{item.directoryName}</code>
                      </p>
                      <p>
                        Processed: <code>{item.move}</code> · Invalid/failed:{' '}
                        <code>{item.moveFailed}</code>
                      </p>
                    </>
                  ) : (
                    <p>
                      {item.host}:{item.port} / {item.database}
                    </p>
                  )}
                </div>
                <span className="config-badge">{item.type === 'file' ? 'File' : 'PostgreSQL'}</span>
                <button
                  onClick={() => open(item)}
                  disabled={!!busy}
                  aria-label={`Edit ${item.name}`}
                >
                  Edit / test
                </button>
                <button
                  className="danger"
                  onClick={() => {
                    setDeleting(item)
                    setNotice(null)
                  }}
                  disabled={!!busy}
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
            Delete <strong>{deleting.name}</strong>? This removes the saved configuration.
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
