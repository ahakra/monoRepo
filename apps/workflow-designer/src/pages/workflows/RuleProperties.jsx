import { RULE_BLOCKS } from './ruleBlocks.js'
import { summarize } from '../rules/conditionalBuilder.js'

export default function RuleProperties({
  node,
  catalog,
  definitions,
  onSelect,
  onRefresh,
  onRemove,
}) {
  const block = RULE_BLOCKS[node.type]
  const rule = catalog.items.find((item) => item.id === node.data.ruleId)
  const fields = definitions.find(
    (item) => item.id === (rule?.spec.fieldDefinitionId || node.data.fieldDefinitionId),
  )
  const words = (value) => value?.replaceAll('_', ' ').toLowerCase()
  return (
    <>
      <label>
        {block.title} definition
        <select
          disabled={catalog.state.loading}
          value={node.data.ruleId}
          onChange={(event) => onSelect(event.target.value)}
        >
          <option value="">
            {catalog.state.loading
              ? 'Loading rules…'
              : `Choose a saved ${block.title.toLowerCase()}`}
          </option>
          {node.data.ruleId && !rule && (
            <option value={node.data.ruleId} disabled>
              {node.data.ruleName || 'Saved rule'} (unavailable)
            </option>
          )}
          {catalog.items.map((item) => (
            <option key={item.id} value={item.id}>
              {item.spec.name}
            </option>
          ))}
        </select>
      </label>
      {catalog.state.error && (
        <p className="workflow-connector-error" role="status">
          {catalog.state.error}
        </p>
      )}
      {catalog.state.loaded && node.data.ruleId && !rule && (
        <p className="workflow-connector-error">
          This rule is no longer available. Choose a replacement. Arrows require matching Fields
          types.
        </p>
      )}
      <div className="workflow-inspector-links">
        <button disabled={catalog.state.loading} onClick={onRefresh}>
          Refresh rules
        </button>
        <a href="#rules">Manage rules ↗</a>
      </div>
      {(rule || fields) && (
        <dl className="workflow-connector-details">
          <dt>Fields definition</dt>
          <dd>{fields?.name || 'Saved Fields definition'}</dd>
          {rule?.spec.description && (
            <>
              <dt>Description</dt>
              <dd>{rule.spec.description}</dd>
            </>
          )}
        </dl>
      )}
      {rule && (
        <div className="workflow-rule-summary">
          <h4>Rule preview</h4>
          {node.type === 'conditionalRule' ? (
            <p>{summarize(rule.spec.expression)}</p>
          ) : (
            <>
              <p>
                If <code>{rule.spec.sourceField}</code> {words(rule.spec.condition)}{' '}
                <code>{JSON.stringify(rule.spec.conditionValue)}</code>
              </p>
              <p>
                Then {words(rule.spec.action)}{' '}
                {rule.spec.actionValue && <code>{JSON.stringify(rule.spec.actionValue)}</code>}
                {rule.spec.action === 'REPLACE' && (
                  <>
                    {' '}
                    with <code>{JSON.stringify(rule.spec.replacementValue)}</code>
                  </>
                )}{' '}
                → <code>{rule.spec.destinationField}</code>
              </p>
            </>
          )}
        </div>
      )}
      <div className="workflow-rule-ports">
        <h4>Ports</h4>
        <p>
          1 input · {block.outputs.length} {block.outputs.length === 1 ? 'output' : 'outputs'}
        </p>
        {block.outputs.map((port) => (
          <span key={port.id}>{port.label}</span>
        ))}
      </div>
      <p className="workflow-property-help">
        {node.type === 'conditionalRule'
          ? 'Route matching records through Matched, and non-matching records through Not matched. Both branches can be connected independently.'
          : 'The single output continues with the record. A match fills the configured destination; a non-match passes through unchanged.'}{' '}
        Connections require the same saved Fields type on both ends. Choose a rule before connecting
        it. This editor defines the flow; rules are not executed here.
      </p>
      <button className="danger" onClick={onRemove}>
        Remove block
      </button>
    </>
  )
}
