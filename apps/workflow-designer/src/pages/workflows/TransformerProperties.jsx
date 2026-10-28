import { transformerPortType } from './workflowModel.js'

export default function TransformerProperties({
  node,
  catalog,
  definitions,
  onSelect,
  onRefresh,
  onRemove,
}) {
  const transformer = catalog.items.find((item) => item.id === node.data.transformerId)
  return (
    <>
      <label>
        Transformer definition
        <select
          disabled={catalog.state.loading}
          value={node.data.transformerId}
          onChange={(event) => onSelect(event.target.value)}
        >
          <option value="">
            {catalog.state.loading ? 'Loading transformers…' : 'Choose a saved transformer'}
          </option>
          {node.data.transformerId && !transformer && (
            <option value={node.data.transformerId} disabled>
              {node.data.transformerName || 'Saved transformer'} (unavailable)
            </option>
          )}
          {catalog.items.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name} · {item.inputs.length} inputs · {item.outputs.length} outputs
            </option>
          ))}
        </select>
      </label>
      {catalog.state.error && (
        <p className="workflow-connector-error" role="status">
          {catalog.state.error}
        </p>
      )}
      {catalog.state.loaded && node.data.transformerId && !transformer && (
        <p className="workflow-connector-error">
          This transformer is no longer available. The draft retains its saved port snapshots.
          Choose a replacement.
        </p>
      )}
      {transformer && (
        <dl className="workflow-connector-details">
          <dt>WASM module</dt>
          <dd>
            {transformer.directory} / {transformer.wasmFileName}
          </dd>
          {transformer.description && (
            <>
              <dt>Description</dt>
              <dd>{transformer.description}</dd>
            </>
          )}
        </dl>
      )}
      <div className="workflow-inspector-links">
        <button disabled={catalog.state.loading} onClick={onRefresh}>
          Refresh transformers
        </button>
        {transformer && <button onClick={() => onSelect(transformer.id)}>Sync ports</button>}
        <a href="#dynamic">Manage transformers ↗</a>
      </div>
      {['inputs', 'outputs'].map((direction) => (
        <div className="workflow-mapper-schema" key={direction}>
          <h4>
            {direction === 'inputs' ? 'Inputs' : 'Outputs'} ({node.data[direction].length})
          </h4>
          {node.data[direction].map((port) => (
            <div key={port.name}>
              <strong>{port.name}</strong>
              <p className="workflow-port-type">Type: {transformerPortType(port, definitions)}</p>
              {port.kind === 'FIELD_LIST' && (
                <div className="field-summary">
                  {Object.entries(port.fields).map(([key, type]) => (
                    <code key={key}>
                      {key}: {type}
                    </code>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
      <p className="workflow-property-help">
        Each defined input and output has its own named port. Fields lists connect to the same saved
        Fields type; dynamic variables connect only to dynamic variables. Variable value types and
        evaluation are not configured yet. Sync ports keeps arrows for retained names, removes
        arrows for deleted ports, and flags type mismatches. This editor does not execute WASM.
      </p>
      <button className="danger" onClick={onRemove}>
        Remove block
      </button>
    </>
  )
}
