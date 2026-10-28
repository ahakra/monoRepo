import {
  comparison,
  group,
  compatible,
  operators,
  operatorsFor,
  unary,
} from './conditionalBuilder.js'

export default function ConditionalBuilder({
  node,
  fields,
  onChange,
  onRemove,
  depth = 1,
  nodeCount,
}) {
  if (node.type === 'GROUP') {
    function childChange(index, child) {
      onChange({
        ...node,
        children: node.children.map((current, i) => (i === index ? child : current)),
      })
    }
    return (
      <fieldset className="conditional-group">
        <legend>{depth === 1 ? 'Conditions' : 'Condition group'}</legend>
        <div className="conditional-group-header">
          <label>
            Match
            <select
              aria-label={`Group at level ${depth} matching`}
              value={node.junction}
              onChange={(e) => onChange({ ...node, junction: e.target.value })}
            >
              <option value="AND">AND — all conditions</option>
              <option value="OR">OR — any condition</option>
            </select>
          </label>
          {onRemove && (
            <button type="button" className="danger" onClick={onRemove}>
              Remove group
            </button>
          )}
        </div>
        <div className="conditional-children">
          {node.children.map((child, index) => (
            <div key={index}>
              {index > 0 && <span className="conditional-junction">{node.junction}</span>}
              <ConditionalBuilder
                node={child}
                fields={fields}
                depth={depth + 1}
                nodeCount={nodeCount}
                onChange={(value) => childChange(index, value)}
                onRemove={
                  node.children.length > 1
                    ? () =>
                        onChange({ ...node, children: node.children.filter((_, i) => i !== index) })
                    : undefined
                }
              />
            </div>
          ))}
        </div>
        <div className="conditional-group-actions">
          <button
            type="button"
            disabled={depth >= 8 || nodeCount >= 100}
            onClick={() => onChange({ ...node, children: [...node.children, comparison()] })}
          >
            + Add condition
          </button>
          <button
            type="button"
            disabled={depth >= 7 || nodeCount > 98}
            onClick={() => onChange({ ...node, children: [...node.children, group()] })}
          >
            + Add group
          </button>
        </div>
      </fieldset>
    )
  }
  const type = fields[node.field]
  const allowed = operatorsFor(type)
  function changeField(field) {
    onChange({ ...comparison(), field })
  }
  function changeOperator(operator) {
    if (unary(operator)) onChange({ type: 'COMPARISON', field: node.field, operator })
    else
      onChange({
        type: 'COMPARISON',
        field: node.field,
        operator,
        operandType: node.operandType || 'VALUE',
        ...(node.operandType === 'FIELD'
          ? { otherField: node.otherField || '' }
          : { value: node.value ?? '' }),
      })
  }
  return (
    <div className="conditional-comparison">
      <div className="config-fields">
        <label>
          Field
          <select required value={node.field} onChange={(e) => changeField(e.target.value)}>
            <option value="">Choose a field</option>
            {Object.entries(fields).map(([key, fieldType]) => (
              <option key={key} value={key}>
                {key} ({fieldType})
              </option>
            ))}
          </select>
        </label>
        <label>
          Operator
          <select value={node.operator} onChange={(e) => changeOperator(e.target.value)}>
            {allowed.map((operator) => (
              <option key={operator} value={operator}>
                {operators[operator]}
              </option>
            ))}
          </select>
        </label>
        {!unary(node.operator) && (
          <>
            <label>
              Compare with
              <select
                value={node.operandType}
                onChange={(e) =>
                  onChange({
                    type: 'COMPARISON',
                    field: node.field,
                    operator: node.operator,
                    operandType: e.target.value,
                    ...(e.target.value === 'FIELD' ? { otherField: '' } : { value: '' }),
                  })
                }
              >
                <option value="VALUE">A value</option>
                <option value="FIELD">Another field</option>
              </select>
            </label>
            {node.operandType === 'FIELD' ? (
              <label>
                Comparison field
                <select
                  required
                  value={node.otherField || ''}
                  onChange={(e) => onChange({ ...node, otherField: e.target.value })}
                >
                  <option value="">Choose a compatible field</option>
                  {Object.entries(fields)
                    .filter(([, rightType]) => type && compatible(type, rightType))
                    .map(([key, fieldType]) => (
                      <option key={key} value={key}>
                        {key} ({fieldType})
                      </option>
                    ))}
                </select>
              </label>
            ) : (
              <label>
                Comparison value{type && ` (${type})`}
                {type === 'boolean' ? (
                  <select
                    required
                    value={String(node.value)}
                    onChange={(e) => onChange({ ...node, value: e.target.value })}
                  >
                    <option value="">Choose true or false</option>
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                ) : type === 'json' ? (
                  <textarea
                    required
                    rows={2}
                    maxLength={10000}
                    value={node.value}
                    onChange={(e) => onChange({ ...node, value: e.target.value })}
                    placeholder={'{"status":"ready"}'}
                  />
                ) : (
                  <input
                    required={!['string', 'text'].includes(type)}
                    type={type === 'date' ? 'date' : 'text'}
                    maxLength={10000}
                    value={node.value}
                    onChange={(e) => onChange({ ...node, value: e.target.value })}
                    placeholder={
                      type === 'datetime'
                        ? '2026-10-28T12:00:00Z'
                        : ['integer', 'long', 'decimal'].includes(type)
                          ? 'Enter a number'
                          : type === 'uuid'
                            ? 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
                            : 'Enter a value'
                    }
                  />
                )}
                {type === 'datetime' && (
                  <small>Use an ISO datetime with Z or an offset such as +02:00.</small>
                )}
              </label>
            )}
          </>
        )}
      </div>
      {onRemove && (
        <button type="button" className="danger conditional-remove" onClick={onRemove}>
          Remove condition
        </button>
      )}
    </div>
  )
}
