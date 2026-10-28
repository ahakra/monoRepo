import { RULE_BLOCKS } from './ruleBlocks.js'
import { validatePorts } from '../dynamic/transformerPorts.js'

export const WORKFLOW_STORAGE_KEY = 'camel-etl.workflow-drafts.v1'

export function createWorkflow() {
  const now = new Date().toISOString()
  return {
    id: crypto.randomUUID(),
    name: 'Untitled workflow',
    description: '',
    createdAt: now,
    updatedAt: now,
    nodes: [
      {
        id: crypto.randomUUID(),
        type: 'dataSource',
        position: { x: 100, y: 180 },
        deletable: false,
        data: { label: 'Data source', connectorId: '', connectorName: '', connectorType: '' },
      },
    ],
    edges: [],
    viewport: { x: 0, y: 0, zoom: 1 },
  }
}

export function createStep(position) {
  return { id: crypto.randomUUID(), type: 'step', position, data: { label: 'Next step' } }
}

export function createMapper(position) {
  return {
    id: crypto.randomUUID(),
    type: 'mapper',
    position,
    data: { label: 'Mapper', mapperId: '', mapperName: '', outputs: [] },
  }
}

export const mapperOutputHandle = (name) => `list:${encodeURIComponent(name)}`
export const transformerHandle = (direction, name) => `${direction}:${encodeURIComponent(name)}`

export function createTransformer(position) {
  return {
    id: crypto.randomUUID(),
    type: 'transformer',
    position,
    data: { label: 'Transformer', transformerId: '', transformerName: '', inputs: [], outputs: [] },
  }
}

export function inputPorts(node) {
  if (node.type === 'dataSource') return []
  if (node.type === 'transformer')
    return node.data.inputs.map((port) => ({
      id: transformerHandle('input', port.name),
      label: port.name,
    }))
  return [{ id: 'in', label: '' }]
}

function defaultInputHandle(node) {
  return node.type === 'transformer' ? null : 'in'
}

export function inputName(connection, nodes) {
  const target = nodes.find((node) => node.id === connection.target)
  return target
    ? inputPorts(target).find(
        (port) => port.id === (connection.targetHandle ?? defaultInputHandle(target)),
      )?.label || ''
    : ''
}

export function transformerPortType(port, definitions) {
  return port.kind === 'DYNAMIC_VARIABLE'
    ? 'Dynamic variable'
    : `List of ${fieldsTypeName(port.fieldDefinitionId, definitions)}`
}

export function applyTransformer(graph, nodeId, transformer) {
  if (!graph.nodes.some((node) => node.id === nodeId && node.type === 'transformer')) return graph
  const snapshot = (ports) =>
    ports.map((port) => ({
      name: port.name,
      kind: port.kind,
      fieldDefinitionId: port.kind === 'FIELD_LIST' ? port.fieldDefinitionId : null,
      fields: port.kind === 'FIELD_LIST' ? { ...port.fields } : {},
    }))
  const inputs = transformer ? snapshot(transformer.inputs) : []
  const outputs = transformer ? snapshot(transformer.outputs) : []
  const nodes = graph.nodes.map((node) =>
    node.id === nodeId
      ? {
          ...node,
          data: {
            ...node.data,
            transformerId: transformer?.id || '',
            transformerName: transformer?.name || '',
            inputs,
            outputs,
          },
        }
      : node,
  )
  const edges = graph.edges
    .filter(
      (edge) =>
        (edge.target !== nodeId ||
          inputs.some((port) => transformerHandle('input', port.name) === edge.targetHandle)) &&
        (edge.source !== nodeId ||
          outputs.some((port) => transformerHandle('output', port.name) === edge.sourceHandle)),
    )
    .map((edge) => (edge.source === nodeId ? { ...edge, label: outputName(edge, nodes) } : edge))
  return { nodes, edges }
}

export function createRule(type, position) {
  if (!RULE_BLOCKS[type]) throw new Error('Choose a supported rule block type.')
  return {
    id: crypto.randomUUID(),
    type,
    position,
    data: { label: RULE_BLOCKS[type].title, ruleId: '', ruleName: '', fieldDefinitionId: '' },
  }
}

export function ruleSelection(rule) {
  return {
    ruleId: rule?.id || '',
    ruleName: rule?.spec.name || '',
    fieldDefinitionId: rule?.spec.fieldDefinitionId || '',
  }
}

export function outputPorts(node) {
  if (!node) return []
  if (node.type === 'transformer')
    return node.data.outputs.map((port) => ({
      id: transformerHandle('output', port.name),
      label: port.name,
    }))
  if (node.type === 'mapper')
    return node.data.outputs.map((output) => ({
      id: mapperOutputHandle(output.name),
      label: output.name,
    }))
  if (RULE_BLOCKS[node.type]) return RULE_BLOCKS[node.type].outputs
  return [{ id: 'out', label: '' }]
}

function defaultOutputHandle(node) {
  return ['dataSource', 'step', 'normalizeRule'].includes(node.type) ? 'out' : null
}

export function outputName(connection, nodes) {
  const source = nodes.find((node) => node.id === connection.source)
  return source
    ? outputPorts(source).find(
        (port) => port.id === (connection.sourceHandle ?? defaultOutputHandle(source)),
      )?.label || ''
    : ''
}

export function fieldsTypeName(id, definitions) {
  return definitions.find((definition) => definition.id === id)?.name || id || 'Choose a type'
}

// Placeholders pass their input through unchanged. Walk both directions through
// them so adding arrows in a different order cannot bypass type constraints.
function portTypes(nodeId, handle, direction, nodes, edges, visited = new Set()) {
  const node = nodes.find((item) => item.id === nodeId)
  if (!node) return new Set([''])
  if (node.type === 'dataSource') return new Set(['raw'])
  if (node.type === 'transformer') {
    const port = node.data[direction === 'input' ? 'inputs' : 'outputs'].find(
      (item) => transformerHandle(direction, item.name) === handle,
    )
    return new Set([
      port?.kind === 'DYNAMIC_VARIABLE'
        ? 'dynamic'
        : port?.fieldDefinitionId
          ? `fields:${port.fieldDefinitionId}`
          : '',
    ])
  }
  if (node.type === 'mapper') {
    if (direction === 'input') return new Set(['raw'])
    const output = node.data.outputs.find((item) => mapperOutputHandle(item.name) === handle)
    return new Set([output?.fieldDefinitionId ? `fields:${output.fieldDefinitionId}` : ''])
  }
  if (RULE_BLOCKS[node.type])
    return new Set([node.data.fieldDefinitionId ? `fields:${node.data.fieldDefinitionId}` : ''])
  if (visited.has(nodeId)) return new Set()
  visited.add(nodeId)
  const types = new Set()
  for (const edge of edges) {
    const connected =
      edge.source === nodeId
        ? portTypes(edge.target, edge.targetHandle ?? 'in', 'input', nodes, edges, visited)
        : edge.target === nodeId
          ? portTypes(edge.source, edge.sourceHandle ?? 'out', 'output', nodes, edges, visited)
          : []
    for (const type of connected) types.add(type)
  }
  return types
}

export function connectionTypesMatch(connection, nodes, edges) {
  const types = new Set([
    ...portTypes(connection.source, connection.sourceHandle ?? 'out', 'output', nodes, edges),
    ...portTypes(connection.target, connection.targetHandle ?? 'in', 'input', nodes, edges),
  ])
  return !types.has('') && types.size <= 1
}

export function incompatibleEdges(graph) {
  return graph.edges.filter((edge) => !connectionTypesMatch(edge, graph.nodes, graph.edges))
}

export function applyMapper(graph, nodeId, mapper) {
  if (!graph.nodes.some((node) => node.id === nodeId && node.type === 'mapper')) return graph
  const outputs = mapper
    ? mapper.outputs.map((output) => ({
        name: output.name,
        fieldDefinitionId: output.fieldDefinitionId,
        fields: { ...output.fields },
      }))
    : []
  const handles = new Set(outputs.map((output) => mapperOutputHandle(output.name)))
  return {
    nodes: graph.nodes.map((node) =>
      node.id === nodeId && node.type === 'mapper'
        ? {
            ...node,
            data: {
              ...node.data,
              mapperId: mapper?.id || '',
              mapperName: mapper?.name || '',
              outputs,
            },
          }
        : node,
    ),
    edges: graph.edges
      .filter((edge) => edge.source !== nodeId || handles.has(edge.sourceHandle))
      .map((edge) =>
        edge.source === nodeId
          ? {
              ...edge,
              label: outputs.find((output) => mapperOutputHandle(output.name) === edge.sourceHandle)
                .name,
            }
          : edge,
      ),
  }
}

export function canConnect(connection, nodes, edges, { checkTypes = true } = {}) {
  const { source, target } = connection
  if (!source || !target || source === target) return false
  if (
    !nodes.some((node) => node.id === source) ||
    !nodes.some((node) => node.id === target && node.type !== 'dataSource')
  )
    return false
  const sourceNode = nodes.find((node) => node.id === source)
  const targetNode = nodes.find((node) => node.id === target)
  const sourceHandle = connection.sourceHandle ?? defaultOutputHandle(sourceNode)
  const targetHandle = connection.targetHandle ?? defaultInputHandle(targetNode)
  if (!inputPorts(targetNode).some((port) => port.id === targetHandle)) return false
  if (!outputPorts(sourceNode).some((port) => port.id === sourceHandle)) return false
  if (checkTypes && !connectionTypesMatch(connection, nodes, edges)) return false
  if (
    edges.some(
      (edge) =>
        edge.source === source &&
        edge.target === target &&
        (edge.sourceHandle ?? defaultOutputHandle(sourceNode)) === sourceHandle &&
        (edge.targetHandle ?? defaultInputHandle(targetNode)) === targetHandle,
    )
  )
    return false
  const pending = [target]
  const visited = new Set()
  while (pending.length) {
    const id = pending.pop()
    if (id === source) return false
    if (visited.has(id)) continue
    visited.add(id)
    for (const edge of edges) if (edge.source === id) pending.push(edge.target)
  }
  return true
}

export function removeSteps(graph, ids) {
  const nodes = graph.nodes.filter((node) => node.type === 'dataSource' || !ids.includes(node.id))
  const remaining = new Set(nodes.map((node) => node.id))
  return {
    nodes,
    edges: graph.edges.filter((edge) => remaining.has(edge.source) && remaining.has(edge.target)),
  }
}

/** Save definition data, never React Flow internals or connector settings/credentials. */
export function serializeWorkflow(workflow, { checkTypes = true } = {}) {
  if (
    typeof workflow.id !== 'string' ||
    !workflow.id ||
    typeof workflow.name !== 'string' ||
    !workflow.name.trim() ||
    workflow.name.length > 120
  )
    throw new Error('Enter a workflow name of up to 120 characters.')
  if (typeof workflow.description !== 'string' || workflow.description.length > 2000)
    throw new Error('Description must have up to 2000 characters.')
  if (
    !Array.isArray(workflow.nodes) ||
    !Array.isArray(workflow.edges) ||
    workflow.nodes.filter((node) => node?.type === 'dataSource').length !== 1
  )
    throw new Error('A workflow must contain exactly one Data source block.')
  const ids = new Set()
  const nodes = workflow.nodes.map((node) => {
    if (
      !node ||
      !['dataSource', 'step', 'mapper', 'transformer', ...Object.keys(RULE_BLOCKS)].includes(
        node.type,
      ) ||
      typeof node.id !== 'string' ||
      !node.id ||
      ids.has(node.id) ||
      !Number.isFinite(node.position?.x) ||
      !Number.isFinite(node.position?.y)
    )
      throw new Error('This workflow contains an invalid block.')
    ids.add(node.id)
    if (
      typeof node.data?.label !== 'string' ||
      !node.data.label.trim() ||
      node.data.label.length > 120
    )
      throw new Error('Each block needs a label of up to 120 characters.')
    const data = { label: node.data.label }
    if (node.type === 'dataSource') {
      for (const key of ['connectorId', 'connectorName', 'connectorType'])
        data[key] = typeof node.data[key] === 'string' ? node.data[key] : ''
    }
    if (node.type === 'mapper') {
      data.mapperId = typeof node.data.mapperId === 'string' ? node.data.mapperId : ''
      data.mapperName = typeof node.data.mapperName === 'string' ? node.data.mapperName : ''
      if (
        !Array.isArray(node.data.outputs) ||
        node.data.outputs.length > 100 ||
        (data.mapperId && !node.data.outputs.length) ||
        (!data.mapperId && node.data.outputs.length)
      )
        throw new Error(
          'Select a mapper with defined output lists, or leave the Mapper block unconfigured.',
        )
      const names = new Set()
      data.outputs = node.data.outputs.map((output) => {
        if (
          !output ||
          typeof output.name !== 'string' ||
          !output.name ||
          output.name.length > 120 ||
          /\s/u.test(output.name) ||
          names.has(output.name) ||
          typeof output.fieldDefinitionId !== 'string' ||
          !output.fieldDefinitionId ||
          !output.fields ||
          typeof output.fields !== 'object' ||
          Array.isArray(output.fields) ||
          !Object.values(output.fields).every((type) => typeof type === 'string')
        )
          throw new Error('The Mapper block has an invalid output list.')
        names.add(output.name)
        return {
          name: output.name,
          fieldDefinitionId: output.fieldDefinitionId,
          fields: { ...output.fields },
        }
      })
    }
    if (node.type === 'transformer') {
      data.transformerId =
        typeof node.data.transformerId === 'string' ? node.data.transformerId : ''
      data.transformerName =
        typeof node.data.transformerName === 'string' ? node.data.transformerName : ''
      for (const direction of ['inputs', 'outputs']) {
        const ports = node.data[direction]
        if (
          !Array.isArray(ports) ||
          (data.transformerId ? validatePorts(ports, direction.slice(0, -1)) : ports.length)
        )
          throw new Error(
            'Select a transformer with valid input and output ports, or leave it unconfigured.',
          )
        data[direction] = ports.map((port) => {
          if (
            port.kind === 'FIELD_LIST' &&
            (!port.fields ||
              typeof port.fields !== 'object' ||
              Array.isArray(port.fields) ||
              !Object.values(port.fields).every((type) => typeof type === 'string') ||
              typeof port.fieldDefinitionId !== 'string')
          )
            throw new Error('The Transformer block has an invalid Fields snapshot.')
          return {
            name: port.name,
            kind: port.kind,
            fieldDefinitionId: port.kind === 'FIELD_LIST' ? port.fieldDefinitionId : null,
            fields: port.kind === 'FIELD_LIST' ? { ...port.fields } : {},
          }
        })
      }
    }
    if (RULE_BLOCKS[node.type]) {
      for (const key of ['ruleId', 'ruleName', 'fieldDefinitionId'])
        data[key] = typeof node.data[key] === 'string' ? node.data[key] : ''
      if (data.ruleId && (!data.ruleName || !data.fieldDefinitionId))
        throw new Error('The rule block is missing its saved rule reference.')
      if (!data.ruleId && (data.ruleName || data.fieldDefinitionId))
        throw new Error('Select a rule or clear its reference.')
    }
    return {
      id: node.id,
      type: node.type,
      position: { x: node.position.x, y: node.position.y },
      ...(node.type === 'dataSource' ? { deletable: false } : {}),
      data,
    }
  })
  const edges = []
  const edgeIds = new Set()
  for (const edge of workflow.edges) {
    if (
      !edge ||
      typeof edge.id !== 'string' ||
      !edge.id ||
      edgeIds.has(edge.id) ||
      !canConnect(edge, nodes, edges, { checkTypes: false })
    )
      throw new Error('This workflow contains an invalid connection or a cycle.')
    edgeIds.add(edge.id)
    edges.push({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle:
        edge.sourceHandle ?? defaultOutputHandle(nodes.find((node) => node.id === edge.source)),
      targetHandle:
        edge.targetHandle ?? defaultInputHandle(nodes.find((node) => node.id === edge.target)),
      type: 'smoothstep',
      ...(outputName(edge, nodes) ? { label: outputName(edge, nodes) } : {}),
    })
  }
  if (checkTypes && incompatibleEdges({ nodes, edges }).length)
    throw new Error(
      'Some arrows have incompatible Fields types or port kinds. Connect the same saved Fields type, or dynamic variables to dynamic variables. Remove or correct the highlighted arrows.',
    )
  const viewport = workflow.viewport
  if (
    !Number.isFinite(viewport?.x) ||
    !Number.isFinite(viewport?.y) ||
    !Number.isFinite(viewport?.zoom) ||
    viewport.zoom < 0.2 ||
    viewport.zoom > 2
  )
    throw new Error('The workflow viewport is invalid.')
  if (
    ![workflow.createdAt, workflow.updatedAt].every(
      (date) => typeof date === 'string' && Number.isFinite(Date.parse(date)),
    )
  )
    throw new Error('The workflow timestamps are invalid.')
  return {
    id: workflow.id,
    name: workflow.name.trim(),
    description: workflow.description.trim(),
    nodes,
    edges,
    viewport: { ...viewport },
    createdAt: workflow.createdAt,
    updatedAt: workflow.updatedAt,
  }
}

export function loadWorkflows(storage) {
  try {
    const raw = (storage ?? window.localStorage).getItem(WORKFLOW_STORAGE_KEY)
    if (!raw) return { workflows: [], error: '' }
    const parsed = JSON.parse(raw)
    if (parsed.version !== 1 || !Array.isArray(parsed.workflows))
      throw new Error('Unsupported draft format')
    // Keep older drafts editable, including arrows made before type validation.
    // Saving/exporting still requires compatible types.
    const workflows = parsed.workflows.map((workflow) =>
      serializeWorkflow(workflow, { checkTypes: false }),
    )
    if (new Set(workflows.map((item) => item.id)).size !== workflows.length)
      throw new Error('Duplicate workflow IDs')
    return { workflows, error: '' }
  } catch {
    return {
      workflows: [],
      error:
        'Browser drafts could not be read. Existing storage has been left untouched. You can still design and export a workflow.',
    }
  }
}

export function saveWorkflows(workflows, storage, { checkTypes = true } = {}) {
  const serialized = workflows.map((workflow) => serializeWorkflow(workflow, { checkTypes }))
  try {
    ;(storage ?? window.localStorage).setItem(
      WORKFLOW_STORAGE_KEY,
      JSON.stringify({ version: 1, workflows: serialized }),
    )
  } catch {
    throw new Error(
      'Could not save in this browser. Keep the editor open or export the draft as JSON.',
    )
  }
  return serialized
}
