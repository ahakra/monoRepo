import { useEffect } from 'react'
import { Handle, Position, useUpdateNodeInternals } from '@xyflow/react'
import {
  mapperOutputHandle,
  fieldsTypeName,
  transformerHandle,
  transformerPortType,
} from './workflowModel.js'
import { RULE_BLOCKS } from './ruleBlocks.js'

export function BlockIcon({ source = false, mapper = false, transformer = false, rule }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {source ? (
        <>
          <ellipse cx="12" cy="5" rx="8" ry="3" />
          <path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0" />
        </>
      ) : rule === 'conditional' ? (
        <>
          <path d="m12 3 7 7-7 7-7-7 7-7ZM12 17v4M19 10h3M2 10h3" />
        </>
      ) : rule === 'normalize' ? (
        <>
          <path d="M4 6h16M4 12h16M4 18h16" />
          <circle cx="8" cy="6" r="2" />
          <circle cx="16" cy="12" r="2" />
          <circle cx="10" cy="18" r="2" />
        </>
      ) : transformer ? (
        <>
          <path d="M3 7h5l3 10h10M3 17h5L11 7h10m-3-3 3 3-3 3m0 4 3 3-3 3" />
        </>
      ) : mapper ? (
        <>
          <path d="M3 12h5m8-6h5m-5 12h5M8 12h4V6h4m-4 6v6h4" />
          <rect x="1" y="9" width="3" height="6" rx="1" />
        </>
      ) : (
        <>
          <rect x="4" y="4" width="16" height="16" rx="4" />
          <path d="M8 12h8m-3-3 3 3-3 3" />
        </>
      )}
    </svg>
  )
}

export function TransformerNode({ id, data, selected }) {
  const updateNodeInternals = useUpdateNodeInternals()
  useEffect(() => {
    updateNodeInternals(id)
  }, [
    id,
    data.inputs,
    data.outputs,
    data.label,
    data.transformerName,
    data.fieldDefinitions,
    updateNodeInternals,
  ])
  return (
    <div className={`workflow-node workflow-node--transformer ${selected ? 'is-selected' : ''}`}>
      <div className="workflow-node-kicker">
        <span>TRANSFORM</span>
        <span>Transformer</span>
      </div>
      <div className="workflow-node-heading">
        <span className="workflow-node-icon">
          <BlockIcon transformer />
        </span>
        <strong>{data.label}</strong>
      </div>
      <p>{data.transformerName || 'Choose a transformer'}</p>
      {!data.transformerId ? (
        <span className="workflow-node-status">Needs configuration</span>
      ) : (
        ['inputs', 'outputs'].map((direction) => (
          <div className="workflow-transformer-ports" key={direction}>
            <h4>{direction === 'inputs' ? 'Inputs' : 'Outputs'}</h4>
            {data[direction].map((port) => (
              <div
                className={`workflow-transformer-port workflow-transformer-port--${direction}`}
                key={port.name}
              >
                <code>{port.name}</code>
                <div className="workflow-port-type">
                  {transformerPortType(port, data.fieldDefinitions || [])}
                </div>
                <Handle
                  type={direction === 'inputs' ? 'target' : 'source'}
                  position={direction === 'inputs' ? Position.Left : Position.Right}
                  id={transformerHandle(direction === 'inputs' ? 'input' : 'output', port.name)}
                  style={{ top: '50%' }}
                  aria-label={`Transformer ${direction === 'inputs' ? 'input' : 'output'} ${port.name}`}
                />
              </div>
            ))}
          </div>
        ))
      )}
    </div>
  )
}

export function RuleNode({ id, type, data, selected }) {
  const block = RULE_BLOCKS[type]
  const updateNodeInternals = useUpdateNodeInternals()
  useEffect(() => {
    updateNodeInternals(id)
  }, [id, data.label, data.ruleName, data.fieldTypeName, updateNodeInternals])
  return (
    <div
      className={`workflow-node workflow-node--rule workflow-node--${block.icon} ${selected ? 'is-selected' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        style={{ top: 68 }}
        aria-label={`${block.title} input`}
      />
      <div className="workflow-node-kicker">
        <span>RULE</span>
        <span>{block.title}</span>
      </div>
      <div className="workflow-node-heading">
        <span className="workflow-node-icon">
          <BlockIcon rule={block.icon} />
        </span>
        <strong>{data.label}</strong>
      </div>
      <p>{data.ruleName || 'Choose a saved rule'}</p>
      <div className="workflow-port-type">
        Type: {data.fieldTypeName || data.fieldDefinitionId || 'Choose a rule'}
      </div>
      <div className="workflow-mapper-outputs">
        {block.outputs.map((port) => (
          <div className="workflow-mapper-output" key={port.id}>
            <span>{port.label}</span>
            <Handle
              type="source"
              position={Position.Right}
              id={port.id}
              style={{ top: '50%', right: 0 }}
              aria-label={`${block.title} output ${port.label}`}
            />
          </div>
        ))}
      </div>
    </div>
  )
}

export function DataSourceNode({ data, selected }) {
  return (
    <div className={`workflow-node workflow-node--source ${selected ? 'is-selected' : ''}`}>
      <div className="workflow-node-kicker">
        <span>START</span>
        <span>Data source</span>
      </div>
      <div className="workflow-node-heading">
        <span className="workflow-node-icon">
          <BlockIcon source />
        </span>
        <strong>{data.label}</strong>
      </div>
      <p>{data.connectorName || 'Choose a connector'}</p>
      <span className={`workflow-node-status ${data.connectorId ? 'is-ready' : ''}`}>
        {data.connectorId
          ? data.connectorType === 'file'
            ? 'File connector'
            : data.connectorType === 'postgres'
              ? 'PostgreSQL connector'
              : data.connectorType
          : 'Needs configuration'}
      </span>
      <Handle type="source" position={Position.Right} id="out" aria-label="Data source output" />
    </div>
  )
}

export function StepNode({ data, selected }) {
  return (
    <div className={`workflow-node workflow-node--step ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} id="in" aria-label="Step input" />
      <div className="workflow-node-kicker">
        <span>STEP</span>
        <span>Placeholder</span>
      </div>
      <div className="workflow-node-heading">
        <span className="workflow-node-icon">
          <BlockIcon />
        </span>
        <strong>{data.label}</strong>
      </div>
      <p>Ready for the next block type</p>
      <Handle type="source" position={Position.Right} id="out" aria-label="Step output" />
    </div>
  )
}

export function MapperNode({ id, data, selected }) {
  const updateNodeInternals = useUpdateNodeInternals()
  useEffect(() => {
    updateNodeInternals(id)
  }, [id, data.outputs, data.label, data.mapperName, data.fieldDefinitions, updateNodeInternals])
  return (
    <div className={`workflow-node workflow-node--mapper ${selected ? 'is-selected' : ''}`}>
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        style={{ top: 68 }}
        aria-label="Mapper input"
      />
      <div className="workflow-node-kicker">
        <span>MAP</span>
        <span>Mapper</span>
      </div>
      <div className="workflow-node-heading">
        <span className="workflow-node-icon">
          <BlockIcon mapper />
        </span>
        <strong>{data.label}</strong>
      </div>
      <p>{data.mapperName || 'Choose a mapper'}</p>
      <div className="workflow-port-type">Input: Raw data</div>
      {!data.outputs.length ? (
        <span className="workflow-node-status">Needs configuration</span>
      ) : (
        <div className="workflow-mapper-outputs">
          {data.outputs.map((output) => (
            <div className="workflow-mapper-output" key={output.name}>
              <span>
                <code>{output.name}</code>
                <small>{Object.keys(output.fields).length} fields</small>
              </span>
              <div className="workflow-port-type">
                Type: {fieldsTypeName(output.fieldDefinitionId, data.fieldDefinitions || [])}
              </div>
              <Handle
                type="source"
                position={Position.Right}
                id={mapperOutputHandle(output.name)}
                style={{ top: '50%', right: 0 }}
                aria-label={`Mapper output ${output.name}`}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
