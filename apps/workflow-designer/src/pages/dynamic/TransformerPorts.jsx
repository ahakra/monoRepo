import { newPort, changePort } from './transformerPorts.js'

export function TransformerPortsEditor({ direction, ports, definitions, onChange }) {
  const title = direction === 'input' ? 'Inputs' : 'Outputs'
  function update(rowId, key, value) {
    onChange(ports.map((port) => (port.rowId === rowId ? changePort(port, key, value) : port)))
  }
  return (
    <section className="mapper-outputs" aria-labelledby={`transformer-${direction}s-title`}>
      <header className="mapper-outputs-header">
        <div>
          <h2 id={`transformer-${direction}s-title`}>{title}</h2>
          <p>Choose a list of a defined Fields type or a dynamic variable for each {direction}.</p>
        </div>
        <button
          type="button"
          disabled={ports.length >= 100}
          onClick={() => onChange([...ports, newPort()])}
        >
          + Add {direction}
        </button>
      </header>
      <p className="mapper-help">
        Use exact, case-sensitive names without spaces. Names must be unique within {direction}s.
      </p>
      {ports.map((port, index) => {
        const selected = definitions.find((definition) => definition.id === port.fieldDefinitionId)
        return (
          <section
            className="mapper-output"
            key={port.rowId}
            aria-label={`${direction} ${index + 1}`}
          >
            <div className="config-fields">
              <label>
                {direction === 'input' ? 'Input name' : 'Output name'}
                <input
                  required
                  maxLength={120}
                  value={port.name}
                  onChange={(e) => update(port.rowId, 'name', e.target.value)}
                  placeholder={direction === 'input' ? 'e.g. CallRecords' : 'e.g. EnrichedCalls'}
                />
              </label>
              <label>
                Type
                <select
                  value={port.kind}
                  onChange={(e) => update(port.rowId, 'kind', e.target.value)}
                >
                  <option value="FIELD_LIST">Fields list</option>
                  <option value="DYNAMIC_VARIABLE">Dynamic variable</option>
                </select>
              </label>
              {port.kind === 'FIELD_LIST' ? (
                <label>
                  Fields definition
                  <select
                    required
                    value={port.fieldDefinitionId}
                    onChange={(e) => update(port.rowId, 'fieldDefinitionId', e.target.value)}
                  >
                    <option value="">Choose a Fields definition</option>
                    {definitions.map((definition) => (
                      <option key={definition.id} value={definition.id}>
                        {definition.name}
                      </option>
                    ))}
                  </select>
                  {!definitions.length && (
                    <small>Create a Fields definition or choose Dynamic variable.</small>
                  )}
                </label>
              ) : (
                <p className="mapper-help">
                  The port name identifies this dynamic variable. Its value, type, and evaluation
                  will be configured later.
                </p>
              )}
            </div>
            {port.kind === 'FIELD_LIST' && selected && (
              <div className="mapper-field-preview">
                <strong>List of {selected.name}</strong>
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
              disabled={ports.length === 1}
              onClick={() => onChange(ports.filter((item) => item.rowId !== port.rowId))}
              aria-label={`Remove ${direction} ${port.name || index + 1}`}
            >
              Remove {direction}
            </button>
          </section>
        )
      })}
    </section>
  )
}

export function TransformerPortsSummary({ title, ports, definitions }) {
  return (
    <div className="mapper-saved-outputs">
      <p>
        <strong>{title}</strong>
      </p>
      {ports.map((port) => (
        <div key={port.name}>
          <p>
            <code>{port.name}</code> →{' '}
            {port.kind === 'DYNAMIC_VARIABLE'
              ? 'Dynamic variable'
              : `List of ${definitions.find((definition) => definition.id === port.fieldDefinitionId)?.name || 'Fields'}`}
          </p>
          {port.kind === 'FIELD_LIST' && (
            <p className="field-summary">
              {Object.entries(port.fields).map(([key, type]) => (
                <code key={key}>
                  {key}: {type}
                </code>
              ))}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}
