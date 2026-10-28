import { useCallback, useEffect, useState } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  MarkerType,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  useReactFlow,
} from '@xyflow/react'
import { connectionApi } from '../configuration/connectionApi.js'
import { mappersApi } from '../mappers/mappersApi.js'
import MapperProperties from './MapperProperties.jsx'
import TransformerProperties from './TransformerProperties.jsx'
import { transformersApi } from '../dynamic/transformersApi.js'
import RuleProperties from './RuleProperties.jsx'
import { rulesApi } from '../rules/rulesApi.js'
import { conditionalApi } from '../rules/conditionalApi.js'
import { fieldsApi } from '../configuration/fieldsApi.js'
import { RULE_BLOCKS } from './ruleBlocks.js'
import { useWorkflowCatalog } from './useWorkflowCatalog.js'
import {
  DataSourceNode,
  StepNode,
  MapperNode,
  TransformerNode,
  RuleNode,
  BlockIcon,
} from './WorkflowNodes.jsx'
import {
  canConnect,
  createStep,
  createMapper,
  createTransformer,
  applyTransformer,
  inputName,
  createRule,
  ruleSelection,
  fieldsTypeName,
  incompatibleEdges,
  applyMapper,
  outputName,
  removeSteps,
  serializeWorkflow,
} from './workflowModel.js'

const nodeTypes = {
  dataSource: DataSourceNode,
  step: StepNode,
  mapper: MapperNode,
  transformer: TransformerNode,
  conditionalRule: RuleNode,
  normalizeRule: RuleNode,
}
const blockFactories = {
  step: createStep,
  mapper: createMapper,
  transformer: createTransformer,
  conditionalRule: (position) => createRule('conditionalRule', position),
  normalizeRule: (position) => createRule('normalizeRule', position),
}
const edgeDefaults = {
  type: 'smoothstep',
  style: { stroke: 'var(--blue)', strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#426579', width: 18, height: 18 },
  labelStyle: { fill: 'var(--blue)', fontSize: 11 },
  labelBgStyle: { fill: 'var(--surface)' },
  labelBgBorderRadius: 4,
}
const dragType = 'application/camel-etl-block'

export default function WorkflowEditor({ draft, isNew, onSave, onBack }) {
  const [name, setName] = useState(draft.name)
  const [description, setDescription] = useState(draft.description)
  const [graph, setGraph] = useState({
    nodes: draft.nodes.map((node) => ({ ...node, selected: node.type === 'dataSource' })),
    edges: draft.edges,
  })
  const [selection, setSelection] = useState({
    kind: 'node',
    id: draft.nodes.find((node) => node.type === 'dataSource').id,
  })
  const [dirty, setDirty] = useState(false)
  const [savedAt, setSavedAt] = useState(null)
  const [notice, setNotice] = useState(null)
  const [connectors, setConnectors] = useState([])
  const [connectorState, setConnectorState] = useState({ loading: true, error: '', loaded: false })
  const [reload, setReload] = useState(0)
  const [mappers, setMappers] = useState([])
  const [mapperState, setMapperState] = useState({ loading: true, error: '', loaded: false })
  const [mapperReload, setMapperReload] = useState(0)
  const [initiallyNew] = useState(isNew)
  const flow = useReactFlow()
  const conditionalRules = useWorkflowCatalog(conditionalApi)
  const normalizeRules = useWorkflowCatalog(rulesApi)
  const fieldDefinitions = useWorkflowCatalog(fieldsApi)
  const transformers = useWorkflowCatalog(transformersApi)

  useEffect(() => {
    let active = true
    connectionApi()
      .then((items) => {
        if (active) {
          setConnectors(items)
          setConnectorState({ loading: false, error: '', loaded: true })
        }
      })
      .catch((error) => {
        if (active) setConnectorState({ loading: false, error: error.message, loaded: false })
      })
    return () => {
      active = false
    }
  }, [reload])

  useEffect(() => {
    let active = true
    mappersApi()
      .then((items) => {
        if (active) {
          setMappers(items)
          setMapperState({ loading: false, error: '', loaded: true })
        }
      })
      .catch((error) => {
        if (active) setMapperState({ loading: false, error: error.message, loaded: false })
      })
    return () => {
      active = false
    }
  }, [mapperReload])

  const onNodesChange = useCallback((changes) => {
    setGraph((current) => {
      const source = current.nodes.find((node) => node.type === 'dataSource')
      const allowed = changes.filter(
        (change) => change.type !== 'remove' || change.id !== source.id,
      )
      const nodes = applyNodeChanges(allowed, current.nodes)
      const ids = new Set(nodes.map((node) => node.id))
      return {
        nodes,
        edges: current.edges.filter((edge) => ids.has(edge.source) && ids.has(edge.target)),
      }
    })
    if (changes.some((change) => !['select', 'dimensions'].includes(change.type))) {
      setDirty(true)
      setNotice(null)
    }
  }, [])
  const onEdgesChange = useCallback((changes) => {
    setGraph((current) => ({ ...current, edges: applyEdgeChanges(changes, current.edges) }))
    if (changes.some((change) => change.type !== 'select')) {
      setDirty(true)
      setNotice(null)
    }
  }, [])
  const isValidConnection = useCallback(
    (connection) => canConnect(connection, graph.nodes, graph.edges),
    [graph],
  )
  const onConnect = useCallback((connection) => {
    setGraph((current) =>
      canConnect(connection, current.nodes, current.edges)
        ? {
            ...current,
            edges: addEdge(
              {
                ...connection,
                id: crypto.randomUUID(),
                label: outputName(connection, current.nodes),
              },
              current.edges,
            ),
          }
        : current,
    )
    setDirty(true)
    setNotice(null)
  }, [])

  function addBlock(type, position) {
    const step = blockFactories[type](position)
    setGraph((current) => ({
      ...current,
      nodes: [
        ...current.nodes.map((node) => ({ ...node, selected: false })),
        { ...step, selected: true },
      ],
    }))
    setSelection({ kind: 'node', id: step.id })
    setDirty(true)
    setNotice(null)
  }
  function addAtCenter(type) {
    const anchor = graph.nodes.find((node) => node.id === selection?.id) || graph.nodes.at(-1)
    const position = { x: anchor.position.x + 320, y: anchor.position.y }
    while (
      graph.nodes.some(
        (node) =>
          Math.abs(node.position.x - position.x) < 200 &&
          Math.abs(node.position.y - position.y) < 160,
      )
    )
      position.y += 180
    addBlock(type, position)
    requestAnimationFrame(() => flow.fitView({ padding: 0.3, maxZoom: 1, duration: 200 }))
  }
  function onDrop(event) {
    event.preventDefault()
    const type = event.dataTransfer.getData(dragType)
    if (!Object.hasOwn(blockFactories, type)) return
    const position = flow.screenToFlowPosition({ x: event.clientX, y: event.clientY })
    addBlock(type, { x: position.x - (type === 'step' ? 112 : 130), y: position.y - 60 })
  }
  function selectSource() {
    const source = graph.nodes.find((node) => node.type === 'dataSource')
    setSelection({ kind: 'node', id: source.id })
    setGraph((current) => ({
      ...current,
      nodes: current.nodes.map((node) => ({ ...node, selected: node.id === source.id })),
    }))
    flow.fitView({ nodes: [source], maxZoom: 1, duration: 250 })
  }
  function updateNode(id, data) {
    setGraph((current) => ({
      ...current,
      nodes: current.nodes.map((node) =>
        node.id === id ? { ...node, data: { ...node.data, ...data } } : node,
      ),
    }))
    setDirty(true)
    setNotice(null)
  }
  function chooseConnector(id) {
    const connector = connectors.find((item) => item.id === id)
    updateNode(selectedNode.id, {
      connectorId: connector?.id || '',
      connectorName: connector?.name || '',
      connectorType: connector?.type || '',
    })
  }
  function currentDraft() {
    return serializeWorkflow({
      ...draft,
      updatedAt: new Date().toISOString(),
      name,
      description,
      ...graph,
      viewport: flow.getViewport(),
    })
  }
  function save() {
    try {
      const saved = onSave(currentDraft())
      setName(saved.name)
      setDescription(saved.description)
      setSavedAt(saved.updatedAt)
      setDirty(false)
      setNotice({ success: true, message: 'Draft saved in this browser.' })
    } catch (error) {
      setNotice({ success: false, message: error.message })
    }
  }
  function exportDraft() {
    try {
      const data = currentDraft()
      const url = URL.createObjectURL(
        new Blob([JSON.stringify({ version: 1, workflow: data }, null, 2)], {
          type: 'application/json',
        }),
      )
      const link = document.createElement('a')
      link.href = url
      link.download = `${data.name.replace(/[^a-z0-9_-]/gi, '_') || 'workflow'}.json`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 0)
    } catch (error) {
      setNotice({ success: false, message: error.message })
    }
  }
  function back() {
    if (
      !dirty ||
      window.confirm(
        'Leave the editor and discard unsaved changes? Save the draft first to keep them.',
      )
    )
      onBack()
  }
  function removeSelection() {
    if (selectedNode && selectedNode.type !== 'dataSource')
      setGraph((current) => removeSteps(current, [selectedNode.id]))
    else if (selectedEdge)
      setGraph((current) => ({
        ...current,
        edges: current.edges.filter((edge) => edge.id !== selectedEdge.id),
      }))
    else return
    setSelection(null)
    setDirty(true)
    setNotice(null)
  }

  const selectedNode =
    selection?.kind === 'node' ? graph.nodes.find((node) => node.id === selection.id) : null
  const selectedEdge =
    selection?.kind === 'edge' ? graph.edges.find((edge) => edge.id === selection.id) : null
  const connector =
    selectedNode?.type === 'dataSource'
      ? connectors.find((item) => item.id === selectedNode.data.connectorId)
      : null
  const sourceConfigured = !!graph.nodes.find((node) => node.type === 'dataSource').data.connectorId
  const invalidEdges = new Set(incompatibleEdges(graph).map((edge) => edge.id))
  const canvasNodes = graph.nodes.map((node) => {
    if (['mapper', 'transformer'].includes(node.type))
      return { ...node, data: { ...node.data, fieldDefinitions: fieldDefinitions.items } }
    if (RULE_BLOCKS[node.type])
      return {
        ...node,
        data: {
          ...node.data,
          fieldTypeName: fieldsTypeName(node.data.fieldDefinitionId, fieldDefinitions.items),
        },
      }
    return node
  })
  const canvasEdges = graph.edges.map((edge) =>
    invalidEdges.has(edge.id)
      ? {
          ...edge,
          style: { stroke: 'var(--error)', strokeWidth: 2 },
          markerEnd: { ...edgeDefaults.markerEnd, color: 'var(--error)' },
        }
      : edge,
  )

  return (
    <main className="page config workflow-editor" aria-labelledby="workflow-editor-title">
      <div className="workflow-editor-topline">
        <button className="workflow-back" onClick={back}>
          ← Workflows
        </button>
        <span className="workflow-draft-badge">Design preview · no execution</span>
      </div>
      <header className="workflow-editor-header">
        <div>
          <h1 id="workflow-editor-title">{name || 'Untitled workflow'}</h1>
          <p>Drag blocks to arrange them. Connect the dots to draw your flow.</p>
        </div>
        <div className="workflow-editor-actions">
          <span className="workflow-save-state" role="status">
            {dirty ? 'Unsaved changes' : savedAt ? 'Saved locally' : 'Browser draft'}
          </span>
          <button onClick={exportDraft}>Export JSON</button>
          <button className="primary" onClick={save}>
            Save draft
          </button>
        </div>
      </header>
      {notice && (
        <div className={`config-notice ${notice.success ? 'success' : 'error'}`} role="status">
          {notice.message}
        </div>
      )}
      {!!invalidEdges.size && (
        <div className="config-notice error" role="status">
          {invalidEdges.size} arrow(s) need matching port types. Fields lists require the same saved
          Fields type; dynamic variables connect only to dynamic variables. Correct or remove the
          highlighted arrows before saving.
          <button
            onClick={() => {
              setGraph((current) => ({
                ...current,
                edges: current.edges.filter((edge) => !invalidEdges.has(edge.id)),
              }))
              setDirty(true)
              setNotice(null)
            }}
          >
            Remove incompatible arrows
          </button>
        </div>
      )}
      <div className="workflow-workspace">
        <aside className="workflow-palette" aria-label="Block library">
          <div className="workflow-panel-heading">
            <span>BUILD YOUR FLOW</span>
            <h2>Blocks</h2>
          </div>
          <button className="workflow-palette-block" onClick={selectSource}>
            <span className="workflow-node-icon">
              <BlockIcon source />
            </span>
            <strong>Data source</strong>
            <small>Starting block · already added</small>
          </button>
          <button
            className="workflow-palette-block workflow-palette-block--mapper"
            draggable
            onDragStart={(event) => {
              event.dataTransfer.setData(dragType, 'mapper')
              event.dataTransfer.effectAllowed = 'copy'
            }}
            onClick={() => addAtCenter('mapper')}
          >
            <span className="workflow-node-icon">
              <BlockIcon mapper />
            </span>
            <strong>Mapper</strong>
            <small>One output for each defined list</small>
          </button>
          <button
            className="workflow-palette-block workflow-palette-block--transformer"
            draggable
            onDragStart={(event) => {
              event.dataTransfer.setData(dragType, 'transformer')
              event.dataTransfer.effectAllowed = 'copy'
            }}
            onClick={() => addAtCenter('transformer')}
          >
            <span className="workflow-node-icon">
              <BlockIcon transformer />
            </span>
            <strong>Transformer</strong>
            <small>Named, typed inputs and outputs</small>
          </button>
          {Object.entries(RULE_BLOCKS).map(([type, block]) => (
            <button
              key={type}
              className={`workflow-palette-block workflow-palette-block--${block.icon}`}
              draggable
              onDragStart={(event) => {
                event.dataTransfer.setData(dragType, type)
                event.dataTransfer.effectAllowed = 'copy'
              }}
              onClick={() => addAtCenter(type)}
            >
              <span className="workflow-node-icon">
                <BlockIcon rule={block.icon} />
              </span>
              <strong>{block.title}</strong>
              <small>
                {type === 'conditionalRule'
                  ? 'Matched and Not matched branches'
                  : 'One input · one output'}
              </small>
            </button>
          ))}
          <button
            className="workflow-palette-block workflow-palette-block--step"
            draggable
            onDragStart={(event) => {
              event.dataTransfer.setData(dragType, 'step')
              event.dataTransfer.effectAllowed = 'copy'
            }}
            onClick={() => addAtCenter('step')}
          >
            <span className="workflow-node-icon">
              <BlockIcon />
            </span>
            <strong>Next step</strong>
            <small>Drag to canvas or click to add</small>
            <span className="workflow-placeholder-tag">Placeholder</span>
          </button>
          <p className="workflow-palette-help">
            We’ll define each processing block together. Next step only lets you try the layout and
            arrows.
          </p>
        </aside>
        <section
          className="workflow-canvas"
          aria-label="Workflow canvas"
          onDrop={onDrop}
          onDragOver={(event) => {
            event.preventDefault()
            event.dataTransfer.dropEffect = 'copy'
          }}
        >
          <ReactFlow
            nodes={canvasNodes}
            edges={canvasEdges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            isValidConnection={isValidConnection}
            defaultEdgeOptions={edgeDefaults}
            defaultViewport={draft.viewport}
            fitView={initiallyNew}
            fitViewOptions={{ padding: 0.8, maxZoom: 1 }}
            minZoom={0.2}
            maxZoom={2}
            snapToGrid
            snapGrid={[16, 16]}
            deleteKeyCode={['Backspace', 'Delete']}
            onNodeClick={(_, node) => setSelection({ kind: 'node', id: node.id })}
            onEdgeClick={(_, edge) => setSelection({ kind: 'edge', id: edge.id })}
            onPaneClick={() => setSelection(null)}
            onMoveEnd={(event) => {
              if (event) setDirty(true)
            }}
            colorMode="light"
          >
            <Background gap={24} size={1.2} color="var(--border)" />
            <Controls showInteractive={false} />
            <MiniMap
              pannable
              zoomable
              nodeColor={(node) => (node.type === 'dataSource' ? '#3f6354' : '#426579')}
            />
          </ReactFlow>
          <div className="workflow-canvas-caption">
            {graph.nodes.length} blocks · {graph.edges.length} arrows
            <span>Scroll to zoom · drag background to pan</span>
          </div>
        </section>
        <aside className="workflow-inspector" aria-label="Workflow and block properties">
          <div className="workflow-panel-heading">
            <span>CONFIGURE</span>
            <h2>Properties</h2>
          </div>
          <div className="workflow-properties">
            <label>
              Workflow name
              <input
                maxLength={120}
                value={name}
                onChange={(event) => {
                  setName(event.target.value)
                  setDirty(true)
                  setNotice(null)
                }}
              />
            </label>
            <label>
              Description
              <textarea
                rows={3}
                maxLength={2000}
                value={description}
                onChange={(event) => {
                  setDescription(event.target.value)
                  setDirty(true)
                  setNotice(null)
                }}
                placeholder="What will this workflow do?"
              />
            </label>
          </div>
          <div className="workflow-block-properties">
            {selectedNode ? (
              <>
                <h3>
                  {selectedNode.type === 'dataSource'
                    ? 'Data source'
                    : ['mapper', 'transformer'].includes(selectedNode.type)
                      ? selectedNode.type === 'mapper'
                        ? 'Mapper'
                        : 'Transformer'
                      : RULE_BLOCKS[selectedNode.type]?.title || 'Next-step placeholder'}
                </h3>
                <label>
                  Block label
                  <input
                    maxLength={120}
                    value={selectedNode.data.label}
                    onChange={(event) => updateNode(selectedNode.id, { label: event.target.value })}
                  />
                </label>
                {selectedNode.type === 'dataSource' ? (
                  <>
                    <label>
                      Connector
                      <select
                        disabled={connectorState.loading}
                        value={selectedNode.data.connectorId}
                        onChange={(event) => chooseConnector(event.target.value)}
                      >
                        <option value="">
                          {connectorState.loading
                            ? 'Loading connectors…'
                            : 'Choose a saved connector'}
                        </option>
                        {selectedNode.data.connectorId && !connector && (
                          <option value={selectedNode.data.connectorId} disabled>
                            {selectedNode.data.connectorName || 'Saved connector'} (unavailable)
                          </option>
                        )}
                        {connectors.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name} ·{' '}
                            {item.type === 'file'
                              ? 'File'
                              : item.type === 'postgres'
                                ? 'PostgreSQL'
                                : item.type}
                          </option>
                        ))}
                      </select>
                    </label>
                    {connectorState.error && (
                      <p className="workflow-connector-error" role="status">
                        {connectorState.error}
                      </p>
                    )}
                    {connectorState.loaded && selectedNode.data.connectorId && !connector && (
                      <p className="workflow-connector-error">
                        This connector is no longer available. Choose a replacement.
                      </p>
                    )}
                    {connector && (
                      <dl className="workflow-connector-details">
                        <dt>Type</dt>
                        <dd>{connector.type === 'file' ? 'File' : 'PostgreSQL'}</dd>
                        <dt>{connector.type === 'file' ? 'Source directory' : 'Database'}</dt>
                        <dd>
                          {connector.type === 'file'
                            ? connector.directoryName
                            : `${connector.host}:${connector.port} / ${connector.database}`}
                        </dd>
                      </dl>
                    )}
                    <div className="workflow-inspector-links">
                      <button
                        onClick={() => {
                          setConnectorState((current) => ({ ...current, loading: true }))
                          setReload((value) => value + 1)
                        }}
                        disabled={connectorState.loading}
                      >
                        Refresh connectors
                      </button>
                      <a href="#connectors">Manage connectors ↗</a>
                    </div>
                    <p className="workflow-property-help">
                      The starting block is required. Its output is the dot on the right. Drag from
                      it to a step’s input dot.
                    </p>
                  </>
                ) : selectedNode.type === 'mapper' ? (
                  <MapperProperties
                    node={selectedNode}
                    mappers={mappers}
                    definitions={fieldDefinitions.items}
                    state={mapperState}
                    onSelect={(id) => {
                      const definition = mappers.find((item) => item.id === id)
                      const next = applyMapper(graph, selectedNode.id, definition)
                      const removed = graph.edges.length - next.edges.length
                      setGraph(next)
                      setDirty(true)
                      setNotice(
                        removed
                          ? {
                              success: true,
                              message: `Mapper outputs updated. ${removed} arrow(s) from removed output lists were removed.`,
                            }
                          : null,
                      )
                    }}
                    onRefresh={() => {
                      setMapperState((current) => ({ ...current, loading: true }))
                      setMapperReload((value) => value + 1)
                    }}
                    onRemove={removeSelection}
                  />
                ) : selectedNode.type === 'transformer' ? (
                  <TransformerProperties
                    node={selectedNode}
                    catalog={transformers}
                    definitions={fieldDefinitions.items}
                    onSelect={(id) => {
                      const next = applyTransformer(
                        graph,
                        selectedNode.id,
                        transformers.items.find((item) => item.id === id),
                      )
                      const removed = graph.edges.length - next.edges.length
                      setGraph(next)
                      setDirty(true)
                      setNotice(
                        removed
                          ? {
                              success: true,
                              message: `Transformer ports updated. ${removed} arrow(s) from removed ports were removed.`,
                            }
                          : null,
                      )
                    }}
                    onRefresh={() => {
                      transformers.refresh()
                      fieldDefinitions.refresh()
                    }}
                    onRemove={removeSelection}
                  />
                ) : RULE_BLOCKS[selectedNode.type] ? (
                  <RuleProperties
                    node={selectedNode}
                    catalog={
                      selectedNode.type === 'conditionalRule' ? conditionalRules : normalizeRules
                    }
                    definitions={fieldDefinitions.items}
                    onSelect={(id) => {
                      const catalog =
                        selectedNode.type === 'conditionalRule' ? conditionalRules : normalizeRules
                      updateNode(
                        selectedNode.id,
                        ruleSelection(catalog.items.find((item) => item.id === id)),
                      )
                    }}
                    onRefresh={() => {
                      const catalog =
                        selectedNode.type === 'conditionalRule' ? conditionalRules : normalizeRules
                      catalog.refresh()
                      fieldDefinitions.refresh()
                    }}
                    onRemove={removeSelection}
                  />
                ) : (
                  <>
                    <p className="workflow-property-help">
                      This block has no processing behavior yet. Use it to try connecting arrows;
                      we’ll define the next real block type together.
                    </p>
                    <button className="danger" onClick={removeSelection}>
                      Remove block
                    </button>
                  </>
                )}
              </>
            ) : selectedEdge ? (
              <>
                <h3>Connection arrow</h3>
                {invalidEdges.has(selectedEdge.id) && (
                  <p className="workflow-connector-error">
                    Incompatible port types. Choose matching definitions or remove this arrow.
                  </p>
                )}
                <p className="workflow-property-help">
                  {graph.nodes.find((node) => node.id === selectedEdge.source)?.data.label}
                  {outputName(selectedEdge, graph.nodes) && (
                    <> / {outputName(selectedEdge, graph.nodes)}</>
                  )}{' '}
                  → {graph.nodes.find((node) => node.id === selectedEdge.target)?.data.label}
                  {inputName(selectedEdge, graph.nodes) && (
                    <> / {inputName(selectedEdge, graph.nodes)}</>
                  )}
                </p>
                <button className="danger" onClick={removeSelection}>
                  Remove arrow
                </button>
              </>
            ) : (
              <p className="workflow-property-help">
                Select a block or arrow to edit its properties.
              </p>
            )}
          </div>
        </aside>
      </div>
      <div className="workflow-editor-footer">
        <span>
          {sourceConfigured
            ? 'Source connector selected'
            : 'Choose a connector for your Data source'}{' '}
          · Drafts may be saved before configuration is complete.
        </span>
        <span>Processing and execution will come later.</span>
      </div>
    </main>
  )
}
