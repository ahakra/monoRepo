import { useEffect, useState } from 'react'
import { fieldsApi } from '../configuration/fieldsApi.js'
import { rulesApi } from './rulesApi.js'
import '../configuration/Configuration.css'
import './Rules.css'

const conditions = {
  STARTS_WITH: 'Starts with',
  ENDS_WITH: 'Ends with',
  CONTAINS: 'Contains',
  EQUALS: 'Equals',
  LENGTH_EQUALS: 'Length equals',
  LENGTH_GREATER_THAN: 'Length greater than',
  LENGTH_LESS_THAN: 'Length less than',
}
const actions = {
  COPY: 'Copy source',
  ADD_PREFIX: 'Add prefix',
  ADD_SUFFIX: 'Add suffix',
  REMOVE_PREFIX: 'Remove prefix',
  REMOVE_SUFFIX: 'Remove suffix',
  REPLACE: 'Replace string',
  TRIM: 'Trim whitespace',
  UPPERCASE: 'Convert to uppercase',
  LOWERCASE: 'Convert to lowercase',
}
const needsText = (action) =>
  ['ADD_PREFIX', 'ADD_SUFFIX', 'REMOVE_PREFIX', 'REMOVE_SUFFIX', 'REPLACE'].includes(action)
const textFields = (definition) =>
  Object.entries(definition?.fields || {})
    .filter(([, type]) => ['string', 'text'].includes(type))
    .map(([key]) => key)

export default function Rules() {
  const [items, setItems] = useState([])
  const [definitions, setDefinitions] = useState([])
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [sample, setSample] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([rulesApi(), fieldsApi()])
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
      const [rules, fields] = await Promise.all([rulesApi(), fieldsApi()])
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
    setForm(
      item
        ? { id: item.id, ...item.spec, replacementValue: item.spec.replacementValue ?? '' }
        : {
            name: '',
            description: '',
            fieldDefinitionId: '',
            sourceField: '',
            destinationField: '',
            condition: 'STARTS_WITH',
            conditionValue: '',
            action: 'REMOVE_PREFIX',
            actionValue: '',
            replacementValue: '',
          },
    )
    setResult(null)
    setSample('')
    setNotice(null)
    setDeleting(null)
  }

  function change(key, value) {
    setForm((current) => ({
      ...current,
      [key]: value,
      ...(key === 'fieldDefinitionId' ? { sourceField: '', destinationField: '' } : {}),
      ...(key === 'condition' ? { conditionValue: '' } : {}),
      ...(key === 'action' ? { actionValue: '', replacementValue: '' } : {}),
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
    const { id, ...spec } = form
    try {
      if (testing) {
        setResult(await rulesApi('/test', 'POST', { spec, record: { [spec.sourceField]: sample } }))
      } else {
        const saved = await rulesApi(id ? `/${id}` : '', id ? 'PUT' : 'POST', spec)
        setItems((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
        setForm(null)
        setNotice({ success: true, message: 'Normalize rule saved.' })
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
      await rulesApi(`/${deleting.id}`, 'DELETE')
      setItems((current) => current.filter((item) => item.id !== deleting.id))
      setDeleting(null)
      setNotice({ success: true, message: 'Rule deleted.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    } finally {
      setBusy('')
    }
  }

  const selected = definitions.find((item) => item.id === form?.fieldDefinitionId)
  const fields = textFields(selected)
  const lengthCondition = form?.condition.startsWith('LENGTH_')

  return (
    <main className="page config rules-page" aria-labelledby="rules-title">
      <div className="config-eyebrow">WORKSPACE / RULES</div>
      <header className="config-header">
        <div>
          <h1 id="rules-title">Rules</h1>
          <p>Normalize text with a condition, an action, and a destination field.</p>
        </div>
        <button
          className="primary"
          disabled={!!form || !!busy || loading || loadError}
          onClick={() => open()}
        >
          + New Normalize rule
        </button>
      </header>
      <div className="config-intro">
        <div>
          <h2>Normalize</h2>
          <p>
            When the source matches, transform its value and fill the destination. Otherwise, keep
            the record unchanged.
          </p>
        </div>
        <span className="config-badge">Text fields only</span>
      </div>
      {notice && (
        <div className={`config-notice ${notice.success ? 'success' : 'error'}`} role="status">
          {notice.message}
        </div>
      )}
      {form ? (
        <section className="config-panel">
          <header>
            <h2>{form.id ? 'Edit Normalize rule' : 'New Normalize rule'}</h2>
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
                    maxLength={120}
                    value={form.name}
                    onChange={(e) => change('name', e.target.value)}
                    placeholder="e.g. Remove country prefix"
                    autoFocus
                  />
                </label>
                <label>
                  Description
                  <textarea
                    maxLength={2000}
                    value={form.description}
                    onChange={(e) => change('description', e.target.value)}
                    placeholder="Describe what this rule does"
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
                      <option
                        key={definition.id}
                        value={definition.id}
                        disabled={!textFields(definition).length}
                      >
                        {definition.name}
                        {!textFields(definition).length ? ' (no text fields)' : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="rules-help">
                  Only string/text fields appear in source and destination selectors.
                  {!definitions.length && (
                    <>
                      {' '}
                      Create a definition in{' '}
                      <a href="#schemas-tables">Schemas &amp; Tables → Fields</a> first.
                    </>
                  )}
                  {selected && !fields.length && ' This definition has no text fields.'}
                </div>
              </div>
              <section className="rules-step" aria-labelledby="rule-if-title">
                <h3 id="rule-if-title">If the source matches</h3>
                <div className="config-fields">
                  <label>
                    Source field
                    <select
                      required
                      value={form.sourceField}
                      onChange={(e) => change('sourceField', e.target.value)}
                      disabled={!fields.length}
                    >
                      <option value="">Choose a text field</option>
                      {fields.map((key) => (
                        <option key={key}>{key}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Condition
                    <select
                      value={form.condition}
                      onChange={(e) => change('condition', e.target.value)}
                    >
                      {Object.entries(conditions).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    {lengthCondition ? 'Number of characters' : 'Condition text'}
                    <input
                      type={lengthCondition ? 'number' : 'text'}
                      min={lengthCondition ? 0 : undefined}
                      max={lengthCondition ? 2147483647 : undefined}
                      step={lengthCondition ? 1 : undefined}
                      required={form.condition !== 'EQUALS'}
                      maxLength={2000}
                      value={form.conditionValue}
                      onChange={(e) => change('conditionValue', e.target.value)}
                    />
                  </label>
                  <p className="rules-help">
                    Text comparisons are case sensitive. Length counts Unicode characters. Missing,
                    null, or non-text source values do not match.
                  </p>
                </div>
              </section>
              <section className="rules-step" aria-labelledby="rule-then-title">
                <h3 id="rule-then-title">Then transform and set</h3>
                <div className="config-fields">
                  <label>
                    Action
                    <select value={form.action} onChange={(e) => change('action', e.target.value)}>
                      {Object.entries(actions).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {needsText(form.action) && (
                    <label>
                      {form.action === 'REPLACE'
                        ? 'Find text'
                        : form.action.endsWith('PREFIX')
                          ? 'Prefix text'
                          : 'Suffix text'}
                      <input
                        required
                        maxLength={2000}
                        value={form.actionValue}
                        onChange={(e) => change('actionValue', e.target.value)}
                      />
                    </label>
                  )}
                  {form.action === 'REPLACE' && (
                    <label>
                      Replace with
                      <input
                        maxLength={2000}
                        value={form.replacementValue}
                        onChange={(e) => change('replacementValue', e.target.value)}
                      />
                      <small>
                        Replaces every exact, case-sensitive occurrence. Leave empty to remove the
                        find text.
                      </small>
                    </label>
                  )}
                  <label>
                    Destination field
                    <select
                      required
                      value={form.destinationField}
                      onChange={(e) => change('destinationField', e.target.value)}
                      disabled={!fields.length}
                    >
                      <option value="">Choose a text field</option>
                      {fields.map((key) => (
                        <option key={key}>{key}</option>
                      ))}
                    </select>
                  </label>
                  <p className="rules-help">
                    A match replaces the destination value. You can choose the source itself.
                    Prefix/suffix removal only removes matching text at that edge.
                  </p>
                </div>
              </section>
              <section className="rules-step" aria-labelledby="rule-test-title">
                <h3 id="rule-test-title">Try a sample</h3>
                <div className="config-fields">
                  <label>
                    Sample source value
                    <input
                      value={sample}
                      onChange={(e) => {
                        setSample(e.target.value)
                        setResult(null)
                      }}
                      placeholder="Enter a value to test"
                    />
                  </label>
                  <div className="rules-test-action">
                    <button type="submit" value="test" disabled={!fields.length}>
                      {busy === 'test' ? 'Testing…' : 'Test rule'}
                    </button>
                  </div>
                </div>
                {result && (
                  <div className="rules-result" role="status">
                    <strong>
                      {result.matched
                        ? 'Matched — destination filled'
                        : 'No match — record unchanged'}
                    </strong>
                    {result.matched && (
                      <p>
                        <code>{form.destinationField}</code> ={' '}
                        <code>{JSON.stringify(result.record[form.destinationField])}</code>
                      </p>
                    )}
                  </div>
                )}
              </section>
              <footer>
                <small>Only matching records receive a destination value.</small>
                <button type="submit" value="save" className="primary" disabled={!fields.length}>
                  {busy === 'save' ? 'Saving…' : 'Save rule'}
                </button>
              </footer>
            </fieldset>
          </form>
        </section>
      ) : (
        <section className="config-panel" aria-label="Saved Normalize rules" aria-busy={loading}>
          <header>
            <h2>
              Normalize rules <b>{items.length}</b>
            </h2>
            <button disabled={loading || !!busy} onClick={refresh}>
              Refresh
            </button>
          </header>
          {loading ? (
            <div className="config-empty">Loading rules…</div>
          ) : loadError ? (
            <div className="config-empty">
              Could not load rules and Fields. Use Refresh to try again.
            </div>
          ) : !items.length ? (
            <div className="config-empty">
              <h3>Create your first Normalize rule</h3>
              <p>
                Choose a text field, check a condition, and write the normalized value to a
                destination.
              </p>
              <button className="primary" onClick={() => open()}>
                + New Normalize rule
              </button>
            </div>
          ) : (
            items.map((item) => (
              <article className="config-row" key={item.id}>
                <span className="config-badge">Normalize</span>
                <div className="config-row-info">
                  <h3>{item.spec.name}</h3>
                  {item.spec.description && <p>{item.spec.description}</p>}
                  <p>
                    {definitions.find((d) => d.id === item.spec.fieldDefinitionId)?.name} ·{' '}
                    <code>{item.spec.sourceField}</code>{' '}
                    {conditions[item.spec.condition].toLowerCase()}{' '}
                    <code>{JSON.stringify(item.spec.conditionValue)}</code>
                  </p>
                  <p>
                    {actions[item.spec.action]}{' '}
                    {needsText(item.spec.action) && (
                      <code>{JSON.stringify(item.spec.actionValue)}</code>
                    )}{' '}
                    {item.spec.action === 'REPLACE' && (
                      <>
                        with <code>{JSON.stringify(item.spec.replacementValue)}</code>{' '}
                      </>
                    )}
                    → <code>{item.spec.destinationField}</code>
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
            Delete Normalize rule <strong>{deleting.spec.name}</strong>?
          </p>
          <button disabled={!!busy} onClick={() => setDeleting(null)}>
            Cancel
          </button>
          <button disabled={!!busy} onClick={remove}>
            {busy === 'delete' ? 'Deleting…' : 'Confirm delete'}
          </button>
        </div>
      )}
    </main>
  )
}
