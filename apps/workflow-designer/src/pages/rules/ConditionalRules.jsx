import { useItemFilters } from '../../components/items/useItemFilters.js'
import {
  ItemFilters,
  ItemTimestamps,
  NoMatchingItems,
} from '../../components/items/ItemFilters.jsx'
import { conditionalFilters } from '../../components/items/itemFilterConfigs.js'
import { useEffect, useState } from 'react'
import { fieldsApi } from '../configuration/fieldsApi.js'
import { conditionalApi as api } from './conditionalApi.js'
import ConditionalBuilder from './ConditionalBuilder.jsx'
import { group, countNodes, mapLiterals, summarize } from './conditionalBuilder.js'

export default function ConditionalRules() {
  const [items, setItems] = useState([])
  const [definitions, setDefinitions] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [form, setForm] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState('')
  const [deleting, setDeleting] = useState(null)
  const [sample, setSample] = useState('{}')
  const [result, setResult] = useState(null)

  const filtered = useItemFilters(items, conditionalFilters(definitions))

  useEffect(() => {
    let active = true
    Promise.all([api(), fieldsApi()])
      .then(([rules, fields]) => {
        if (active) {
          setItems(rules)
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
      const [rules, fields] = await Promise.all([api(), fieldsApi()])
      setItems(rules)
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
    if (item) {
      const fields =
        definitions.find((definition) => definition.id === item.spec.fieldDefinitionId)?.fields ||
        {}
      const expression = mapLiterals(item.spec.expression, fields, true)
      setForm({
        id: item.id,
        ...item.spec,
        expression:
          expression.type === 'GROUP'
            ? expression
            : { type: 'GROUP', junction: 'AND', children: [expression] },
      })
    } else setForm({ name: '', description: '', fieldDefinitionId: '', expression: group() })
    setNotice(null)
    setResult(null)
    setDeleting(null)
    setSample('{}')
  }

  function change(key, value) {
    setForm((current) => ({
      ...current,
      [key]: value,
      ...(key === 'fieldDefinitionId' ? { expression: group() } : {}),
    }))
    setResult(null)
    setNotice(null)
  }

  async function submit(event) {
    event.preventDefault()
    const testing = event.nativeEvent.submitter?.value === 'test'
    setBusy(testing ? 'test' : 'save')
    setNotice(null)
    setResult(null)
    try {
      const { id, ...draft } = form
      const spec = { ...draft, expression: mapLiterals(draft.expression, selected?.fields || {}) }
      if (testing) {
        let record
        try {
          record = JSON.parse(sample)
        } catch {
          throw new Error('Enter a valid JSON sample record.')
        }
        if (!record || Array.isArray(record) || typeof record !== 'object')
          throw new Error('The sample record must be a JSON object.')
        // Avoid rounding large integer values in the browser before sending them.
        setResult(
          await api('/test', 'POST', `{"spec":${JSON.stringify(spec)},"record":${sample}}`, true),
        )
      } else {
        const saved = await api(id ? `/${id}` : '', id ? 'PUT' : 'POST', spec)
        setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
        setForm(null)
        setNotice({ success: true, message: 'Conditional rule saved.' })
      }
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
      setNotice({ success: true, message: 'Conditional rule deleted.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  const selected = definitions.find((definition) => definition.id === form?.fieldDefinitionId)
  return (
    <section aria-labelledby="conditional-title">
      <header className="config-header">
        <div>
          <h2 id="conditional-title">Conditional rules</h2>
          <p>Compare any field with a value or another field. Combine groups with AND / OR.</p>
        </div>
        <button
          className="primary"
          disabled={!!form || !!busy || loading || loadError}
          onClick={() => open()}
        >
          + New Conditional rule
        </button>
      </header>
      <div className="config-intro">
        <div>
          <h2>Match complex conditions</h2>
          <p>
            Each group matches all (AND) or any (OR) of its children. Nest groups to control how
            conditions combine.
          </p>
        </div>
        <span className="config-badge">All field types</span>
      </div>
      {notice && (
        <div className={`config-notice ${notice.success ? 'success' : 'error'}`} role="status">
          {notice.message}
        </div>
      )}
      {form ? (
        <section className="config-panel">
          <header>
            <h2>{form.id ? 'Edit Conditional rule' : 'New Conditional rule'}</h2>
            <button
              disabled={!!busy}
              onClick={() => {
                setForm(null)
                setNotice(null)
                setResult(null)
              }}
            >
              Cancel
            </button>
          </header>
          <form onSubmit={submit}>
            <fieldset disabled={!!busy}>
              <div className="config-fields">
                <label>
                  Rule name
                  <input
                    required
                    autoFocus
                    maxLength={120}
                    value={form.name}
                    onChange={(e) => change('name', e.target.value)}
                    placeholder="e.g. Completed calls after cutoff"
                  />
                </label>
                <label>
                  Description
                  <textarea
                    maxLength={2000}
                    value={form.description}
                    onChange={(e) => change('description', e.target.value)}
                    rows={2}
                  />
                </label>
                <label>
                  Fields definition
                  <select
                    required
                    value={form.fieldDefinitionId}
                    onChange={(e) => change('fieldDefinitionId', e.target.value)}
                  >
                    <option value="">Choose Fields</option>
                    {definitions.map((definition) => (
                      <option key={definition.id} value={definition.id}>
                        {definition.name}
                      </option>
                    ))}
                  </select>
                </label>
                <p className="rules-help">
                  All fields are available, including numbers, dates, datetimes, booleans, UUIDs,
                  and JSON. Comparison fields must have compatible types.{' '}
                  {!definitions.length && (
                    <a href="#schemas-tables">Create a Fields definition first.</a>
                  )}
                </p>
              </div>
              {selected && (
                <div className="conditional-builder">
                  <ConditionalBuilder
                    node={form.expression}
                    fields={selected.fields}
                    nodeCount={countNodes(form.expression)}
                    onChange={(value) => change('expression', value)}
                  />
                  <p className="rules-help">
                    Up to 8 levels and 100 conditions/groups. Text comparisons are case sensitive;
                    text ordering is alphabetical by character value. Datetimes require a timezone
                    offset. Missing/null values only match explicit null checks; invalid data values
                    do not match.
                  </p>
                </div>
              )}
              <section className="rules-step" aria-labelledby="conditional-test-title">
                <h3 id="conditional-test-title">Try a sample record</h3>
                <div className="config-fields">
                  <label>
                    Sample record (JSON)
                    <textarea
                      rows={7}
                      value={sample}
                      onChange={(e) => {
                        setSample(e.target.value)
                        setResult(null)
                      }}
                      placeholder={
                        '{"start_time":"2026-10-28T12:00:00Z","end_time":"2026-10-28T13:00:00Z"}'
                      }
                    />
                  </label>
                  <div className="rules-test-action">
                    <button type="submit" value="test" disabled={!selected}>
                      {busy === 'test' ? 'Testing…' : 'Test conditions'}
                    </button>
                  </div>
                </div>
                {result && (
                  <div className="rules-result" role="status">
                    <strong>
                      {result.matched
                        ? 'Matched — conditions satisfied'
                        : 'No match — conditions not satisfied'}
                    </strong>
                    <p>The sample record is unchanged.</p>
                  </div>
                )}
              </section>
              <footer>
                <small>
                  Conditional rules return a match result and leave the record unchanged.
                </small>
                <button type="submit" value="save" className="primary" disabled={!selected}>
                  {busy === 'save' ? 'Saving…' : 'Save rule'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved Conditional rules" aria-busy={loading}>
          <header>
            <h2>
              Conditional rules <b>{items.length}</b>
            </h2>
            <button disabled={loading || !!busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          <ItemFilters filter={filtered} label="Conditional rules" disabled={loading} />
          {loading ? (
            <div className="config-empty">Loading Conditional rules…</div>
          ) : loadError ? (
            <div className="config-empty">
              Could not load rules and Fields. Use Refresh to try again.
            </div>
          ) : !items.length ? (
            <div className="config-empty">
              <h3>Create your first Conditional rule</h3>
              <p>Build conditions such as end_time &gt; start_time AND status equals completed.</p>
              <button className="primary" onClick={() => open()}>
                + New Conditional rule
              </button>
            </div>
          ) : !filtered.items.length ? (
            <NoMatchingItems filter={filtered} />
          ) : (
            filtered.items.map((item) => (
              <article className="config-row" key={item.id}>
                <span className="config-badge">Conditional</span>
                <div className="config-row-info">
                  <h3>{item.spec.name}</h3>
                  <ItemTimestamps item={item} />
                  {item.spec.description && <p>{item.spec.description}</p>}
                  <p>
                    {
                      definitions.find(
                        (definition) => definition.id === item.spec.fieldDefinitionId,
                      )?.name
                    }
                  </p>
                  <p>
                    <code>{summarize(item.spec.expression)}</code>
                  </p>
                </div>
                <button
                  disabled={!!busy}
                  onClick={() => open(item)}
                  aria-label={`Edit ${item.spec.name}`}
                >
                  Edit / test
                </button>
                <button
                  className="danger"
                  disabled={!!busy}
                  onClick={() => {
                    setDeleting(item)
                    setNotice(null)
                  }}
                  aria-label={`Delete ${item.spec.name}`}
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
            Delete Conditional rule <strong>{deleting.spec.name}</strong>?
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
