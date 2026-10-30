import assert from 'node:assert/strict'
import test from 'node:test'
import {
  WORKFLOW_STORAGE_KEY,
  createWorkflow,
  createStep,
  createDataDestination,
  inputPorts,
  canConnect,
  removeSteps,
  serializeWorkflow,
  loadWorkflows,
  saveWorkflows,
  createMapper,
  applyMapper,
  mapperOutputHandle,
  outputName,
  createRule,
  ruleSelection,
  outputPorts,
  connectionTypesMatch,
  incompatibleEdges,
  fieldsTypeName,
} from '../src/pages/workflows/workflowModel.js'

const mapperDefinition = {
  id: 'mapper-id',
  name: 'CDR parser',
  directory: '/private/modules',
  wasmFileName: 'parser.wasm',
  outputs: [
    {
      name: 'CallRecords',
      fieldDefinitionId: 'calls-type',
      fields: { caller: 'string', duration: 'long' },
    },
    { name: 'callRecords', fieldDefinitionId: 'calls-type', fields: { caller: 'string' } },
    { name: 'Customers', fieldDefinitionId: 'customers-type', fields: { name: 'string' } },
  ],
}

function mapperGraph() {
  const workflow = createWorkflow()
  const mapper = createMapper({ x: 420, y: 180 })
  const target = createStep({ x: 740, y: 180 })
  const configured = applyMapper(
    { nodes: [...workflow.nodes, mapper, target], edges: [] },
    mapper.id,
    mapperDefinition,
  )
  configured.edges.push({
    id: 'input',
    source: workflow.nodes[0].id,
    target: mapper.id,
    sourceHandle: 'out',
    targetHandle: 'in',
  })
  return { ...workflow, ...configured }
}

function graph() {
  const workflow = createWorkflow()
  workflow.nodes.push(createStep({ x: 420, y: 180 }), createStep({ x: 740, y: 180 }))
  const [source, first, second] = workflow.nodes
  workflow.edges = [
    { id: 'a', source: source.id, target: first.id },
    { id: 'b', source: first.id, target: second.id },
  ]
  return workflow
}

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

test('new workflows start with exactly one required Data source and no execution configuration', () => {
  const workflow = createWorkflow()
  assert.equal(workflow.nodes.length, 1)
  assert.equal(workflow.nodes[0].type, 'dataSource')
  assert.equal(workflow.nodes[0].deletable, false)
  assert.equal(workflow.nodes[0].data.connectorId, '')
  assert.deepEqual(workflow.edges, [])
  assert.notEqual(createWorkflow().id, workflow.id)
  assert.equal(serializeWorkflow(workflow).nodes.length, 1)
})

test('arrows reject cycles, duplicates, missing nodes, self-connections, and incoming source edges', () => {
  const { nodes, edges } = graph()
  const [source, first, second] = nodes
  assert.equal(canConnect({ source: source.id, target: second.id }, nodes, edges), true)
  for (const connection of [
    { source: source.id, target: first.id },
    { source: first.id, target: first.id },
    { source: second.id, target: first.id },
    { source: second.id, target: source.id },
    { source: 'missing', target: second.id },
    { source: first.id, target: 'missing' },
  ])
    assert.equal(canConnect(connection, nodes, edges), false)
})

test('deleting a step removes its arrows while the required Data source survives deletion', () => {
  const workflow = graph()
  const [source, first, second] = workflow.nodes
  const remaining = removeSteps(workflow, [source.id, first.id])
  assert.deepEqual(
    remaining.nodes.map((node) => node.id),
    [source.id, second.id],
  )
  assert.deepEqual(remaining.edges, [])
  assert.equal(workflow.nodes.length, 3)
  assert.equal(workflow.edges.length, 2)
})

test('serialization keeps positions and connector references but strips credentials and rendering state', () => {
  const workflow = graph()
  workflow.nodes[0].selected = true
  workflow.nodes[0].measured = { width: 224, height: 150 }
  workflow.nodes[0].data = {
    ...workflow.nodes[0].data,
    connectorId: 'connector-id',
    connectorName: 'Inbox',
    connectorType: 'file',
    password: 'secret',
    settings: { directoryName: '/private' },
  }
  workflow.viewport = { x: 50, y: 10, zoom: 0.75 }
  const serialized = serializeWorkflow(workflow)
  assert.deepEqual(serialized.viewport, workflow.viewport)
  assert.deepEqual(serialized.nodes[1].position, workflow.nodes[1].position)
  assert.equal(serialized.nodes[0].data.connectorId, 'connector-id')
  assert.equal('settings' in serialized.nodes[0].data, false)
  assert.equal('password' in serialized.nodes[0].data, false)
  assert.equal('selected' in serialized.nodes[0], false)
  assert.equal('measured' in serialized.nodes[0], false)
  assert.equal(serialized.edges[0].targetHandle, 'in')
})

test('local drafts survive reload with the same graph and source identity', () => {
  const storage = memoryStorage()
  const workflow = graph()
  const saved = saveWorkflows([workflow], storage)
  const loaded = loadWorkflows(storage)
  assert.equal(loaded.error, '')
  assert.deepEqual(loaded.workflows, saved)
  assert.equal(loaded.workflows[0].nodes[0].id, workflow.nodes[0].id)
  saveWorkflows([], storage)
  assert.deepEqual(loadWorkflows(storage).workflows, [])
})

test('corrupt or unsupported local drafts are reported without overwriting storage', () => {
  const storage = memoryStorage()
  for (const raw of [
    '{broken',
    '{"version":2,"workflows":[]}',
    JSON.stringify({ version: 1, workflows: [{ ...createWorkflow(), nodes: [] }] }),
  ]) {
    storage.setItem(WORKFLOW_STORAGE_KEY, raw)
    const result = loadWorkflows(storage)
    assert.ok(result.error)
    assert.deepEqual(result.workflows, [])
    assert.equal(storage.getItem(WORKFLOW_STORAGE_KEY), raw)
  }
  assert.ok(
    loadWorkflows({
      getItem() {
        throw new Error('Blocked')
      },
    }).error,
  )
  assert.throws(
    () =>
      saveWorkflows([createWorkflow()], {
        setItem() {
          throw new Error('Quota exceeded')
        },
      }),
    /Could not save/,
  )
})

test('invalid saved graphs are rejected, while incomplete connector drafts remain saveable', () => {
  const workflow = graph()
  assert.throws(() => serializeWorkflow({ ...workflow, name: ' ' }), /name/)
  assert.throws(
    () => serializeWorkflow({ ...workflow, nodes: workflow.nodes.slice(1) }),
    /exactly one/,
  )
  assert.throws(
    () =>
      serializeWorkflow({
        ...workflow,
        nodes: [...workflow.nodes, { ...workflow.nodes[0], id: 'another-source' }],
      }),
    /exactly one/,
  )
  assert.throws(
    () =>
      serializeWorkflow({
        ...workflow,
        edges: [
          ...workflow.edges,
          { id: 'cycle', source: workflow.nodes[2].id, target: workflow.nodes[1].id },
        ],
      }),
    /cycle/,
  )
  assert.throws(
    () => serializeWorkflow({ ...workflow, viewport: { x: 0, y: 0, zoom: NaN } }),
    /viewport/,
  )
  assert.equal(serializeWorkflow(workflow).nodes[0].data.connectorId, '')
})

test('mapper selection creates an exact output snapshot, with distinct case-sensitive port identities', () => {
  const workflow = mapperGraph()
  const mapper = workflow.nodes[1]
  assert.equal(mapper.type, 'mapper')
  assert.equal(mapper.data.outputs.length, mapperDefinition.outputs.length)
  assert.deepEqual(mapper.data.outputs, mapperDefinition.outputs)
  assert.notEqual(mapper.data.outputs[0].fields, mapperDefinition.outputs[0].fields)
  assert.notEqual(mapperOutputHandle('CallRecords'), mapperOutputHandle('callRecords'))
  assert.notEqual(mapperOutputHandle('a/b'), mapperOutputHandle('a%2Fb'))
  assert.equal('directory' in mapper.data, false)
  assert.equal(applyMapper(workflow, workflow.nodes[0].id, mapperDefinition), workflow)
})

test('each mapper output can connect independently, including different outputs to the same target', () => {
  const { nodes, edges } = mapperGraph()
  const [, mapper, target] = nodes
  const first = {
    id: 'first',
    source: mapper.id,
    target: target.id,
    sourceHandle: mapperOutputHandle('CallRecords'),
    targetHandle: 'in',
  }
  const second = { ...first, id: 'second', sourceHandle: mapperOutputHandle('callRecords') }
  assert.equal(canConnect(first, nodes, edges), true)
  assert.equal(canConnect(second, nodes, [...edges, first]), true)
  assert.equal(canConnect(first, nodes, [...edges, first]), false)
  assert.equal(canConnect({ ...first, sourceHandle: 'out' }, nodes, edges), false)
  assert.equal(
    canConnect({ ...first, sourceHandle: mapperOutputHandle('missing') }, nodes, edges),
    false,
  )
  assert.equal(
    canConnect({ source: target.id, target: mapper.id }, nodes, [...edges, first]),
    false,
  )
  assert.equal(outputName(first, nodes), 'CallRecords')
})

test('mapper outputs and arrow handles survive local storage round trips without being reset to out', () => {
  const workflow = mapperGraph()
  const [, mapper, target] = workflow.nodes
  workflow.edges.push({
    id: 'mapped',
    source: mapper.id,
    target: target.id,
    sourceHandle: mapperOutputHandle('CallRecords'),
    targetHandle: 'in',
  })
  const storage = memoryStorage()
  saveWorkflows([workflow], storage)
  const loaded = loadWorkflows(storage).workflows[0]
  assert.equal(loaded.nodes[1].data.mapperId, mapperDefinition.id)
  assert.deepEqual(loaded.nodes[1].data.outputs, mapperDefinition.outputs)
  assert.equal(loaded.edges[1].sourceHandle, mapperOutputHandle('CallRecords'))
  assert.equal(loaded.edges[1].label, 'CallRecords')
})

test('syncing a changed mapper preserves retained list arrows and input arrows, removing stale outputs', () => {
  const workflow = mapperGraph()
  const [, mapper, target] = workflow.nodes
  for (const [index, output] of mapper.data.outputs.entries())
    workflow.edges.push({
      id: `output-${index}`,
      source: mapper.id,
      target: target.id,
      sourceHandle: mapperOutputHandle(output.name),
      targetHandle: 'in',
    })
  const updated = applyMapper(workflow, mapper.id, {
    ...mapperDefinition,
    outputs: [
      mapperDefinition.outputs[2],
      mapperDefinition.outputs[0],
      { name: 'NewList', fieldDefinitionId: 'new-type', fields: { value: 'decimal' } },
    ],
  })
  assert.deepEqual(
    updated.nodes[1].data.outputs.map((output) => output.name),
    ['Customers', 'CallRecords', 'NewList'],
  )
  assert.deepEqual(
    updated.edges.map((edge) => edge.id),
    ['input', 'output-0', 'output-2'],
  )
  const cleared = applyMapper(updated, mapper.id, null)
  assert.deepEqual(
    cleared.edges.map((edge) => edge.id),
    ['input'],
  )
  assert.deepEqual(cleared.nodes[1].data.outputs, [])
  assert.equal(cleared.nodes[1].data.mapperId, '')
  assert.equal(
    canConnect({ source: mapper.id, target: target.id }, cleared.nodes, cleared.edges),
    false,
  )
})

test('drafts allow an unconfigured mapper but reject nonexistent handles and invalid output definitions', () => {
  const workflow = createWorkflow()
  workflow.nodes.push(createMapper({ x: 420, y: 180 }))
  assert.equal(serializeWorkflow(workflow).nodes[1].data.outputs.length, 0)
  const configured = mapperGraph()
  const bad = structuredClone(configured)
  bad.nodes[1].data.outputs[1].name = 'CallRecords'
  assert.throws(() => serializeWorkflow(bad), /invalid output list/)
  configured.edges.push({
    id: 'stale',
    source: configured.nodes[1].id,
    target: configured.nodes[2].id,
    sourceHandle: mapperOutputHandle('deleted'),
  })
  assert.throws(() => serializeWorkflow(configured), /invalid connection/)
})

function ruleGraph() {
  const workflow = createWorkflow()
  const conditional = createRule('conditionalRule', { x: 420, y: 180 })
  const normalize = createRule('normalizeRule', { x: 740, y: 180 })
  for (const node of [conditional, normalize])
    Object.assign(
      node.data,
      ruleSelection({ id: node.type, spec: { name: node.type, fieldDefinitionId: 'fields-id' } }),
    )
  workflow.nodes.push(conditional, normalize)
  workflow.edges = [
    { id: 'yes', source: conditional.id, target: normalize.id, sourceHandle: 'matched' },
    { id: 'no', source: conditional.id, target: normalize.id, sourceHandle: 'not-matched' },
  ]
  return workflow
}

test('conditional branches connect independently and require a valid explicit port', () => {
  const graph = ruleGraph()
  const [, conditional, normalize] = graph.nodes
  assert.deepEqual(
    outputPorts(conditional).map((port) => port.id),
    ['matched', 'not-matched'],
  )
  assert.deepEqual(
    outputPorts(normalize).map((port) => port.id),
    ['out'],
  )
  assert.equal(canConnect(graph.edges[0], graph.nodes, []), true)
  assert.equal(canConnect(graph.edges[1], graph.nodes, graph.edges.slice(0, 1)), true)
  assert.equal(canConnect(graph.edges[1], graph.nodes, graph.edges), false)
  for (const sourceHandle of [undefined, 'out', 'notMatched', 'missing'])
    assert.equal(canConnect({ ...graph.edges[1], sourceHandle }, graph.nodes, []), false)
  assert.equal(
    canConnect({ source: normalize.id, target: conditional.id }, graph.nodes, graph.edges),
    false,
  )
  assert.equal(outputName(graph.edges[0], graph.nodes), 'Matched')
  assert.equal(outputName(graph.edges[1], graph.nodes), 'Not matched')
  assert.throws(() => createRule('unknown', { x: 0, y: 0 }), /supported rule/)
})

test('normalize accepts input and provides only its single output', () => {
  const graph = ruleGraph()
  const target = createStep({ x: 1060, y: 180 })
  graph.nodes.push(target)
  const connection = { source: graph.nodes[2].id, target: target.id }
  assert.equal(canConnect(connection, graph.nodes, graph.edges), true)
  assert.equal(
    canConnect({ ...connection, sourceHandle: 'matched' }, graph.nodes, graph.edges),
    false,
  )
  assert.equal(canConnect({ ...connection, targetHandle: 'out' }, graph.nodes, graph.edges), false)
  graph.edges.push({ ...connection, id: 'normalized' })
  const restored = serializeWorkflow(graph)
  assert.equal(restored.edges.at(-1).sourceHandle, 'out')
  assert.equal(restored.edges.at(-1).label, 'Output')
})

test('rule references and branches survive storage while executable definitions are excluded', () => {
  const graph = ruleGraph()
  const rule = {
    id: 'saved-rule',
    spec: { name: 'Recent records', fieldDefinitionId: 'fields-id', expression: { private: true } },
  }
  Object.assign(graph.nodes[1].data, ruleSelection(rule), { spec: rule.spec })
  const storage = memoryStorage()
  saveWorkflows([graph], storage)
  const loaded = loadWorkflows(storage).workflows[0]
  assert.deepEqual(loaded.nodes[1].data, {
    label: 'Conditional rule',
    ruleId: rule.id,
    ruleName: rule.spec.name,
    fieldDefinitionId: 'fields-id',
  })
  assert.deepEqual(
    loaded.edges.map((edge) => [edge.sourceHandle, edge.label]),
    [
      ['matched', 'Matched'],
      ['not-matched', 'Not matched'],
    ],
  )
  Object.assign(loaded.nodes[1].data, ruleSelection(null))
  assert.equal(loaded.edges.length, 2)
  assert.throws(() => serializeWorkflow(loaded), /incompatible Fields types/)
  Object.assign(loaded.nodes[1].data, ruleSelection(rule))
  loaded.edges[1].sourceHandle = 'out'
  assert.throws(() => serializeWorkflow(loaded), /invalid connection/)
})

test('removing a rule removes incident branches and keeps the required source', () => {
  const graph = ruleGraph()
  const removed = removeSteps(graph, [graph.nodes[0].id, graph.nodes[1].id])
  assert.deepEqual(
    removed.nodes.map((node) => node.type),
    ['dataSource', 'normalizeRule'],
  )
  assert.deepEqual(removed.edges, [])
  graph.nodes[1].data.ruleId = 'incomplete-reference'
  graph.nodes[1].data.ruleName = ''
  assert.throws(() => serializeWorkflow(graph), /missing its saved rule reference/)
})

function typedGraph() {
  const workflow = mapperGraph()
  const conditional = createRule('conditionalRule', { x: 740, y: 360 })
  const normalize = createRule('normalizeRule', { x: 1060, y: 360 })
  for (const node of [conditional, normalize])
    Object.assign(
      node.data,
      ruleSelection({ id: node.type, spec: { name: node.type, fieldDefinitionId: 'calls-type' } }),
    )
  workflow.nodes.push(conditional, normalize)
  return workflow
}

test('mapper outputs only connect to rules using the identical Fields definition', () => {
  const workflow = typedGraph()
  const [, mapper, , conditional, normalize] = workflow.nodes
  const match = {
    id: 'typed',
    source: mapper.id,
    sourceHandle: mapperOutputHandle('CallRecords'),
    target: conditional.id,
  }
  assert.equal(canConnect(match, workflow.nodes, workflow.edges), true)
  const mismatch = { ...match, sourceHandle: mapperOutputHandle('Customers') }
  assert.equal(canConnect(mismatch, workflow.nodes, workflow.edges), false)
  assert.equal(
    canConnect({ ...match, source: workflow.nodes[0].id, sourceHandle: 'out' }, workflow.nodes, []),
    false,
  )
  assert.equal(
    canConnect(
      { source: conditional.id, sourceHandle: 'matched', target: normalize.id },
      workflow.nodes,
      [],
    ),
    true,
  )
  assert.equal(
    canConnect(
      { source: conditional.id, sourceHandle: 'not-matched', target: normalize.id },
      workflow.nodes,
      [],
    ),
    true,
  )
  normalize.data.fieldDefinitionId = 'customers-type'
  assert.equal(
    canConnect(
      { source: conditional.id, sourceHandle: 'matched', target: normalize.id },
      workflow.nodes,
      [],
    ),
    false,
  )
  conditional.data = { ...conditional.data, ...ruleSelection(null) }
  assert.equal(canConnect(match, workflow.nodes, []), false)
  assert.equal(fieldsTypeName('calls-type', [{ id: 'calls-type', name: 'Calls' }]), 'Calls')
  assert.equal(fieldsTypeName('calls-type', []), 'calls-type')
})

test('placeholders propagate types in either drawing order and cannot merge different Fields types', () => {
  const workflow = typedGraph()
  const [, mapper, step, conditional] = workflow.nodes
  const fromMapper = {
    source: mapper.id,
    sourceHandle: mapperOutputHandle('Customers'),
    target: step.id,
  }
  const toRule = { source: step.id, target: conditional.id }
  assert.equal(canConnect(fromMapper, workflow.nodes, []), true)
  assert.equal(canConnect(toRule, workflow.nodes, [fromMapper]), false)
  assert.equal(canConnect(toRule, workflow.nodes, []), true)
  assert.equal(canConnect(fromMapper, workflow.nodes, [toRule]), false)
  const calls = { ...fromMapper, sourceHandle: mapperOutputHandle('CallRecords') }
  assert.equal(canConnect(toRule, workflow.nodes, [calls]), true)
  assert.equal(canConnect(fromMapper, workflow.nodes, [calls]), false)
  const raw = { source: workflow.nodes[0].id, target: step.id }
  assert.equal(canConnect(raw, workflow.nodes, [toRule]), false)
})

test('type changes invalidate existing arrows, including mapper synchronization and persisted graphs', () => {
  const workflow = typedGraph()
  const [, mapper, , conditional] = workflow.nodes
  const edge = {
    id: 'typed',
    source: mapper.id,
    sourceHandle: mapperOutputHandle('CallRecords'),
    target: conditional.id,
  }
  workflow.edges.push(edge)
  assert.deepEqual(incompatibleEdges(workflow), [])
  assert.equal(serializeWorkflow(workflow).edges.length, 2)
  const updated = applyMapper(workflow, mapper.id, {
    ...mapperDefinition,
    outputs: mapperDefinition.outputs.map((output) => ({
      ...output,
      fieldDefinitionId: 'different-type',
    })),
  })
  Object.assign(updated, {
    id: workflow.id,
    name: workflow.name,
    description: workflow.description,
    createdAt: workflow.createdAt,
    updatedAt: workflow.updatedAt,
    viewport: workflow.viewport,
  })
  assert.equal(updated.edges.length, 2)
  assert.equal(connectionTypesMatch(edge, updated.nodes, updated.edges), false)
  assert.deepEqual(
    incompatibleEdges(updated).map((item) => item.id),
    ['typed'],
  )
  assert.throws(() => serializeWorkflow(updated), /incompatible Fields types/)
  const storage = memoryStorage()
  const raw = JSON.stringify({ version: 1, workflows: [updated] })
  storage.setItem(WORKFLOW_STORAGE_KEY, raw)
  const loaded = loadWorkflows(storage)
  assert.equal(loaded.error, '')
  assert.equal(loaded.workflows.length, 1)
  assert.equal(loaded.workflows[0].edges.length, 2)
  assert.equal(storage.getItem(WORKFLOW_STORAGE_KEY), raw)
  assert.throws(() => saveWorkflows(loaded.workflows, storage), /incompatible Fields types/)
})

test('destinations receive raw and typed data but cannot send data or bypass upstream types', () => {
  const workflow = mapperGraph()
  const [source, mapper, step] = workflow.nodes
  const destination = createDataDestination({ x: 1060, y: 180 })
  workflow.nodes.push(destination)
  assert.deepEqual(inputPorts(destination), [{ id: 'in', label: '' }])
  assert.deepEqual(outputPorts(destination), [])
  for (const connection of [
    { source: source.id, target: destination.id },
    { source: mapper.id, sourceHandle: mapperOutputHandle('Customers'), target: destination.id },
    { source: step.id, target: destination.id },
  ])
    assert.equal(canConnect(connection, workflow.nodes, workflow.edges), true)
  assert.equal(canConnect({ source: destination.id, target: step.id }, workflow.nodes, []), false)
  assert.equal(
    canConnect(
      { source: source.id, target: destination.id, targetHandle: 'out' },
      workflow.nodes,
      [],
    ),
    false,
  )
  const rawInput = { id: 'raw', source: source.id, target: step.id }
  const terminal = { id: 'terminal', source: step.id, target: destination.id }
  assert.equal(
    canConnect(
      { source: mapper.id, sourceHandle: mapperOutputHandle('Customers'), target: step.id },
      workflow.nodes,
      [...workflow.edges, rawInput, terminal],
    ),
    false,
  )
})

test('destination drafts preserve connector references, strip secrets, and allow removal', () => {
  const workflow = createWorkflow()
  const destination = createDataDestination({ x: 420, y: 180 })
  workflow.nodes.push(destination)
  assert.equal(serializeWorkflow(workflow).nodes[1].data.connectorId, '')
  Object.assign(destination.data, {
    connectorId: 'destination-connector',
    connectorName: 'Output',
    connectorType: 'postgres',
    password: 'secret',
    settings: { host: 'private-host' },
  })
  workflow.edges.push({ id: 'output', source: workflow.nodes[0].id, target: destination.id })
  const storage = memoryStorage()
  const saved = saveWorkflows([workflow], storage)
  assert.deepEqual(loadWorkflows(storage).workflows, saved)
  assert.deepEqual(saved[0].nodes[1].data, {
    label: 'Data destination',
    connectorId: 'destination-connector',
    connectorName: 'Output',
    connectorType: 'postgres',
  })
  assert.equal(saved[0].edges[0].targetHandle, 'in')
  const removed = removeSteps(workflow, [destination.id])
  assert.equal(removed.nodes.length, 1)
  assert.deepEqual(removed.edges, [])
})
