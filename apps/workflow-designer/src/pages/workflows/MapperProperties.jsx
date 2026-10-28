import { fieldsTypeName } from './workflowModel.js'

export default function MapperProperties({
  node,
  mappers,
  state,
  definitions,
  onSelect,
  onRefresh,
  onRemove,
}) {
  const mapper = mappers.find((item) => item.id === node.data.mapperId)
  return (
    <>
      <label>
        Mapper definition
        <select
          disabled={state.loading}
          value={node.data.mapperId}
          onChange={(event) => onSelect(event.target.value)}
        >
          <option value="">{state.loading ? 'Loading mappers…' : 'Choose a saved mapper'}</option>
          {node.data.mapperId && !mapper && (
            <option value={node.data.mapperId} disabled>
              {node.data.mapperName || 'Saved mapper'} (unavailable)
            </option>
          )}
          {mappers.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name} · {item.outputs.length} output lists
            </option>
          ))}
        </select>
      </label>
      {state.error && (
        <p className="workflow-connector-error" role="status">
          {state.error}
        </p>
      )}
      {state.loaded && node.data.mapperId && !mapper && (
        <p className="workflow-connector-error">
          This mapper is no longer available. Choose a replacement. The draft retains its saved
          output snapshot.
        </p>
      )}
      {mapper && (
        <dl className="workflow-connector-details">
          <dt>WASM module</dt>
          <dd>
            {mapper.directory} / {mapper.wasmFileName}
          </dd>
          {mapper.description && (
            <>
              <dt>Description</dt>
              <dd>{mapper.description}</dd>
            </>
          )}
        </dl>
      )}
      <div className="workflow-inspector-links">
        <button disabled={state.loading} onClick={onRefresh}>
          Refresh mappers
        </button>
        {mapper && <button onClick={() => onSelect(mapper.id)}>Sync outputs</button>}
        <a href="#dynamic">Manage mappers ↗</a>
      </div>
      <div className="workflow-mapper-schema">
        <h4>Output lists ({node.data.outputs.length})</h4>
        {node.data.outputs.map((output) => (
          <div key={output.name}>
            <strong>{output.name}</strong>
            <p className="workflow-port-type">
              Type: {fieldsTypeName(output.fieldDefinitionId, definitions)}
            </p>
            <div className="field-summary">
              {Object.entries(output.fields).map(([key, type]) => (
                <code key={key}>
                  {key}: {type}
                </code>
              ))}
            </div>
          </div>
        ))}
        {!node.data.outputs.length && (
          <p className="workflow-property-help">Choose a mapper to create its output handles.</p>
        )}
      </div>
      <p className="workflow-property-help">
        Every defined output list has its own named output dot. Connect each list to a rule using
        the same Fields type. Sync loads the latest definition; arrows from removed lists are
        removed, while matching list names keep their arrows.
      </p>
      <button className="danger" onClick={onRemove}>
        Remove block
      </button>
    </>
  )
}
