import { useEffect, useRef, useState } from 'react'
import { tablesApi as api } from './tablesApi.js'

export default function SchemaChanges({ item, onClose, onNotice, onBusy }) {
  const [plan, setPlan] = useState(null)
  const [selected, setSelected] = useState([])
  const [acknowledged, setAcknowledged] = useState(false)
  const [loading, setLoading] = useState(true)
  const [applying, setApplying] = useState(false)
  const [error, setError] = useState('')
  const heading = useRef(null)

  useEffect(() => {
    heading.current?.focus()
    let active = true
    api(`/${item.id}/schema-plan`)
      .then((result) => {
        if (active) setPlan(result)
      })
      .catch((failure) => {
        if (active) setError(failure.message)
      })
      .finally(() => {
        if (active) {
          setLoading(false)
          onBusy('')
        }
      })
    return () => {
      active = false
    }
  }, [item.id, onBusy])

  async function refresh() {
    setLoading(true)
    setError('')
    setPlan(null)
    setSelected([])
    setAcknowledged(false)
    onBusy('schema-review')
    try {
      setPlan(await api(`/${item.id}/schema-plan`))
    } catch (failure) {
      setError(failure.message)
    } finally {
      setLoading(false)
      onBusy('')
    }
  }

  async function apply() {
    setApplying(true)
    setError('')
    onBusy('schema-apply')
    try {
      const result = await api(`/${item.id}/schema-changes`, 'POST', {
        fingerprint: plan.fingerprint,
        changeIds: selected,
        acknowledgeDataLoss: acknowledged,
      })
      onNotice({ success: true, message: result.message })
      onClose()
    } catch (failure) {
      setError(failure.message)
      // Any failure, including a lost response after commit, requires a fresh review.
      setPlan(null)
      setSelected([])
      setAcknowledged(false)
    } finally {
      setApplying(false)
      onBusy('')
    }
  }

  const destructive = plan?.changes.some(
    (change) => selected.includes(change.id) && change.destructive,
  )
  const busy = loading || applying
  return (
    <section
      className="config-panel schema-review"
      aria-labelledby="schema-review-title"
      aria-busy={busy}
    >
      <header>
        <div>
          <h2 id="schema-review-title" tabIndex={-1} ref={heading}>
            Review PostgreSQL column changes
          </h2>
          <p>
            {item.name} ·{' '}
            <code>
              {item.schemaName}.{item.tableName}
            </code>
          </p>
        </div>
        <button disabled={busy} onClick={onClose}>
          Close
        </button>
      </header>
      <div className="schema-review-body">
        <p>
          The plan compares the actual database columns with your saved table mapping. Removing a
          mapping only schedules a possible DROP; it takes effect when you select and apply it here.
        </p>
        <p>
          To change a field: remove it from all table mappings and save, edit the field, add it back
          to the table and save, then review the ALTER here. A renamed field appears as ADD and
          DROP; data is not copied between them.
        </p>
        {error && (
          <div className="config-notice error" role="alert">
            {error}
          </div>
        )}
        {loading ? (
          <p role="status">Reading PostgreSQL columns…</p>
        ) : plan && plan.changes.length === 0 ? (
          <p role="status">The table columns match the saved mapping. No changes are needed.</p>
        ) : (
          plan && (
            <>
              <div className="schema-review-actions">
                <button
                  disabled={busy}
                  onClick={() => {
                    setSelected(plan.changes.map((change) => change.id))
                    setAcknowledged(false)
                  }}
                >
                  Select all changes
                </button>
                <button
                  disabled={busy}
                  onClick={() => {
                    setSelected([])
                    setAcknowledged(false)
                  }}
                >
                  Clear selection
                </button>
              </div>
              <ul className="schema-change-list">
                {plan.changes.map((change) => (
                  <li key={change.id}>
                    <label>
                      <input
                        type="checkbox"
                        disabled={busy}
                        checked={selected.includes(change.id)}
                        onChange={(event) => {
                          setSelected(
                            event.target.checked
                              ? [...selected, change.id]
                              : selected.filter((id) => id !== change.id),
                          )
                          setAcknowledged(false)
                        }}
                      />
                      <span>
                        <strong>
                          {change.action} <code>{change.columnName}</code>
                        </strong>
                        <small>
                          {change.currentType || 'New column'} →{' '}
                          {change.targetType || 'Column removed'}
                        </small>
                        {change.destructive && (
                          <small className="schema-data-loss">
                            {change.action === 'DROP'
                              ? 'Permanently removes this column and all its values.'
                              : 'Converts existing values; conversions can lose precision or fail.'}
                          </small>
                        )}
                      </span>
                    </label>
                    <pre>{change.sql}</pre>
                  </li>
                ))}
              </ul>
              {destructive && (
                <label className="schema-acknowledgment">
                  <input
                    type="checkbox"
                    checked={acknowledged}
                    disabled={busy}
                    onChange={(event) => setAcknowledged(event.target.checked)}
                  />
                  <span>
                    I understand the selected DROP or ALTER changes can permanently remove or
                    convert data.
                  </span>
                </label>
              )}
            </>
          )
        )}
      </div>
      <footer>
        <small>
          Only selected changes run. PostgreSQL dependencies can block changes; failed transactions
          are rolled back.
        </small>
        <div>
          <button disabled={busy} onClick={refresh}>
            Refresh plan
          </button>
          <button
            className="primary"
            disabled={busy || !plan || selected.length === 0 || (destructive && !acknowledged)}
            onClick={apply}
          >
            {applying
              ? 'Applying…'
              : `Apply ${selected.length} selected change${selected.length === 1 ? '' : 's'}`}
          </button>
        </div>
      </footer>
    </section>
  )
}
