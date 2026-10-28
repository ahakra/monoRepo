import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createWorkflow,
  createTransformer,
  applyTransformer,
  transformerHandle,
  createMapper,
  applyMapper,
  mapperOutputHandle,
  createRule,
  ruleSelection,
  createStep,
  inputPorts,
  outputPorts,
  inputName,
  canConnect,
  incompatibleEdges,
  serializeWorkflow,
  saveWorkflows,
  loadWorkflows,
  transformerPortType,
} from '../src/pages/workflows/workflowModel.js'

const list = (name, id) => ({
  name,
  kind: 'FIELD_LIST',
  fieldDefinitionId: id,
  fields: { name: 'string' },
})
const variable = (name) => ({ name, kind: 'DYNAMIC_VARIABLE', fieldDefinitionId: null, fields: {} })
const definition = {
  id: 'transformer-id',
  name: 'Enrich calls',
  directory: '/private/modules',
  wasmFileName: 'enrich.wasm',
  inputs: [list('Calls', 'calls-type'), variable('Context'), list('Customers', 'customers-type')],
  outputs: [list('Enriched', 'enriched-type'), variable('Result'), list('Calls', 'calls-type')],
}
const handle = transformerHandle
function workflowGraph() {
  const workflow = createWorkflow()
  const transformer = createTransformer({ x: 700, y: 100 })
  const mapper = createMapper({ x: 400, y: 100 })
  const next = applyMapper(
    { nodes: [...workflow.nodes, mapper, transformer], edges: [] },
    mapper.id,
    {
      id: 'mapper-id',
      name: 'Parse',
      outputs: [list('Parsed', 'calls-type'), list('Customers', 'customers-type')],
    },
  )
  const graph = applyTransformer(next, transformer.id, definition)
  graph.edges.push({ id: 'raw', source: workflow.nodes[0].id, target: mapper.id })
  return { ...workflow, ...graph }
}
const inputEdge = (workflow) => ({
  id: 'typed-input',
  source: workflow.nodes[1].id,
  sourceHandle: mapperOutputHandle('Parsed'),
  target: workflow.nodes[2].id,
  targetHandle: handle('input', 'Calls'),
})
const storage = () => {
  let raw
  return {
    getItem: () => raw,
    setItem: (_key, value) => {
      raw = value
    },
  }
}

test('transformer snapshots show every typed port with independent exact identities', () => {
  const workflow = workflowGraph()
  const transformer = workflow.nodes[2]
  assert.deepEqual(transformer.data.inputs, definition.inputs)
  assert.deepEqual(transformer.data.outputs, definition.outputs)
  assert.notEqual(transformer.data.inputs[0].fields, definition.inputs[0].fields)
  assert.deepEqual(
    inputPorts(transformer).map((port) => port.id),
    definition.inputs.map((port) => handle('input', port.name)),
  )
  assert.deepEqual(
    outputPorts(transformer).map((port) => port.id),
    definition.outputs.map((port) => handle('output', port.name)),
  )
  assert.notEqual(handle('input', 'Calls'), handle('output', 'Calls'))
  assert.notEqual(handle('input', 'Calls'), handle('input', 'calls'))
  assert.notEqual(handle('input', 'a/b'), handle('input', 'a%2Fb'))
  assert.equal(
    transformerPortType(definition.inputs[0], [{ id: 'calls-type', name: 'Call records' }]),
    'List of Call records',
  )
  assert.equal(transformerPortType(definition.inputs[1], []), 'Dynamic variable')
  assert.equal('wasmFileName' in transformer.data, false)
})

test('mapper and rule connections must match the specific transformer input type', () => {
  const workflow = workflowGraph()
  const [, , transformer] = workflow.nodes
  const edge = inputEdge(workflow)
  assert.equal(canConnect(edge, workflow.nodes, workflow.edges), true)
  for (const targetHandle of [
    undefined,
    'in',
    handle('input', 'Customers'),
    handle('input', 'Context'),
    handle('output', 'Calls'),
    handle('input', 'calls'),
  ])
    assert.equal(canConnect({ ...edge, targetHandle }, workflow.nodes, workflow.edges), false)
  assert.equal(
    canConnect({ ...edge, source: workflow.nodes[0].id, sourceHandle: 'out' }, workflow.nodes, []),
    false,
  )
  workflow.edges.push(edge)
  assert.equal(canConnect(edge, workflow.nodes, workflow.edges), false)
  const second = {
    ...edge,
    id: 'customers',
    sourceHandle: mapperOutputHandle('Customers'),
    targetHandle: handle('input', 'Customers'),
  }
  assert.equal(canConnect(second, workflow.nodes, workflow.edges), true)
  const rule = createRule('normalizeRule', { x: 1000, y: 100 })
  Object.assign(
    rule.data,
    ruleSelection({ id: 'rule-id', spec: { name: 'Trim', fieldDefinitionId: 'enriched-type' } }),
  )
  workflow.nodes.push(rule)
  const out = {
    source: transformer.id,
    sourceHandle: handle('output', 'Enriched'),
    target: rule.id,
  }
  assert.equal(canConnect(out, workflow.nodes, workflow.edges), true)
  assert.equal(
    canConnect({ ...out, sourceHandle: handle('output', 'Calls') }, workflow.nodes, workflow.edges),
    false,
  )
  assert.equal(
    canConnect(
      { ...out, sourceHandle: handle('output', 'Result') },
      workflow.nodes,
      workflow.edges,
    ),
    false,
  )
  assert.equal(
    canConnect(
      { source: rule.id, target: transformer.id, targetHandle: handle('input', 'Calls') },
      workflow.nodes,
      [],
    ),
    false,
  )
})

test('dynamic variables connect only to dynamic variables and cycles remain blocked', () => {
  const workflow = workflowGraph()
  const first = workflow.nodes[2]
  const second = createTransformer({ x: 1000, y: 100 })
  Object.assign(
    workflow,
    applyTransformer({ ...workflow, nodes: [...workflow.nodes, second] }, second.id, definition),
  )
  const edge = {
    id: 'variable',
    source: first.id,
    sourceHandle: handle('output', 'Result'),
    target: second.id,
    targetHandle: handle('input', 'Context'),
  }
  assert.equal(canConnect(edge, workflow.nodes, []), true)
  assert.equal(
    canConnect({ ...edge, targetHandle: handle('input', 'Calls') }, workflow.nodes, []),
    false,
  )
  assert.equal(
    canConnect({ ...edge, sourceHandle: handle('output', 'Calls') }, workflow.nodes, []),
    false,
  )
  assert.equal(
    canConnect({ ...edge, source: second.id, target: first.id }, workflow.nodes, [edge]),
    false,
  )
  assert.equal(canConnect({ ...edge, target: first.id }, workflow.nodes, []), false)
})

test('placeholder type checks respect named transformer inputs in either drawing order', () => {
  const workflow = workflowGraph()
  const step = createStep({ x: 550, y: 100 })
  workflow.nodes.push(step)
  const incoming = { ...inputEdge(workflow), target: step.id, targetHandle: 'in' }
  const outgoing = {
    source: step.id,
    target: workflow.nodes[2].id,
    targetHandle: handle('input', 'Customers'),
  }
  assert.equal(canConnect(outgoing, workflow.nodes, []), true)
  assert.equal(canConnect(incoming, workflow.nodes, [outgoing]), false)
  assert.equal(canConnect(outgoing, workflow.nodes, [incoming]), false)
  assert.equal(
    canConnect({ ...outgoing, targetHandle: handle('input', 'Calls') }, workflow.nodes, [incoming]),
    true,
  )
})

test('sync ports retains exact-name arrows, prunes removed inputs/outputs, and flags changed types', () => {
  const workflow = workflowGraph()
  const transformer = workflow.nodes[2]
  const target = createStep({ x: 1000, y: 100 })
  workflow.nodes.push(target)
  const input = inputEdge(workflow)
  const output = {
    id: 'output',
    source: transformer.id,
    sourceHandle: handle('output', 'Calls'),
    target: target.id,
  }
  workflow.edges.push(input, output)
  const changed = applyTransformer(workflow, transformer.id, {
    ...definition,
    inputs: [list('Calls', 'other-type'), variable('Context')],
    outputs: [definition.outputs[2]],
  })
  assert.deepEqual(
    changed.edges.map((edge) => edge.id),
    ['raw', input.id, output.id],
  )
  assert.deepEqual(
    incompatibleEdges(changed).map((edge) => edge.id),
    [input.id],
  )
  const renamed = applyTransformer(changed, transformer.id, {
    ...definition,
    inputs: [list('calls', 'calls-type')],
    outputs: [variable('Result')],
  })
  assert.deepEqual(
    renamed.edges.map((edge) => edge.id),
    ['raw'],
  )
  const cleared = applyTransformer(workflow, transformer.id, null)
  assert.deepEqual(
    cleared.edges.map((edge) => edge.id),
    ['raw'],
  )
  assert.deepEqual(cleared.nodes[2].data.inputs, [])
  assert.equal(cleared.nodes[2].data.transformerId, '')
})

test('named target handles and port schemas round trip through save/load without credentials or UI data', () => {
  const workflow = workflowGraph()
  const edge = inputEdge(workflow)
  workflow.edges.push(edge)
  workflow.nodes[2].data.password = 'secret'
  workflow.nodes[2].data.fieldDefinitions = [{ id: 'private-ui' }]
  const memory = storage()
  const saved = saveWorkflows([workflow], memory)
  const loaded = loadWorkflows(memory)
  assert.equal(loaded.error, '')
  assert.deepEqual(loaded.workflows, saved)
  const restored = loaded.workflows[0]
  assert.equal(restored.edges[1].targetHandle, handle('input', 'Calls'))
  assert.equal(inputName(restored.edges[1], restored.nodes), 'Calls')
  assert.deepEqual(restored.nodes[2].data.inputs, definition.inputs)
  assert.equal('password' in restored.nodes[2].data, false)
  assert.equal('fieldDefinitions' in restored.nodes[2].data, false)
  const bad = structuredClone(restored)
  bad.edges[1].targetHandle = 'in'
  assert.throws(() => serializeWorkflow(bad), /invalid connection/)
})

test('unconfigured transformers are saveable but have no ports, invalid snapshots are rejected', () => {
  const workflow = createWorkflow()
  workflow.nodes.push(createTransformer({ x: 400, y: 100 }))
  assert.equal(serializeWorkflow(workflow).nodes[1].data.inputs.length, 0)
  assert.equal(
    canConnect({ source: workflow.nodes[0].id, target: workflow.nodes[1].id }, workflow.nodes, []),
    false,
  )
  const invalid = workflowGraph()
  invalid.nodes[2].data.inputs.push(invalid.nodes[2].data.inputs[0])
  assert.throws(() => serializeWorkflow(invalid), /valid input and output/)
  invalid.nodes[2].data.inputs.pop()
  invalid.nodes[2].data.outputs[0].fields = null
  assert.throws(() => serializeWorkflow(invalid), /invalid Fields snapshot/)
})
