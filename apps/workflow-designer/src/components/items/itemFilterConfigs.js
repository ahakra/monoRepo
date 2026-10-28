const search = (id, label, values) => ({ id, label, values })
const facet = (id, label, values, options) => ({ id, label, values, options })
const definitionName = (definitions, id) =>
  definitions.find((definition) => definition.id === id)?.name || ''
const definitionOptions = (definitions) =>
  definitions.map((definition) => ({ value: definition.id, label: definition.name }))
const name = search('name', 'Name', (item) => item.name)
const description = search('description', 'Description', (item) => item.description)
const ruleName = search('name', 'Name', (item) => item.spec.name)
const ruleDescription = search('description', 'Description', (item) => item.spec.description)

export const connectionFilters = {
  searchFields: [
    name,
    search('type', 'Connector type', (item) => item.type),
    search('host', 'Host', (item) => item.host),
    search('database', 'Database', (item) => item.database),
    search('schema', 'Default schema / search path', (item) => item.currentSchema),
    search('username', 'Username', (item) => item.username),
    search('directories', 'File directories', (item) => [
      item.directoryName,
      item.move,
      item.moveFailed,
      item.preMove,
    ]),
    search('fileFilters', 'File filters and encoding', (item) => [
      item.fileName,
      item.include,
      item.exclude,
      item.includeExt,
      item.excludeExt,
      item.charset,
    ]),
  ],
  facets: [facet('type', 'Connector type', (item) => item.type)],
}

export const fieldFilters = {
  searchFields: [
    name,
    search('fields', 'Field keys and types', (item) => Object.entries(item.fields)),
    search('usage', 'Used by', (item) =>
      (item.usedBy || []).map((reference) => reference.tableName),
    ),
  ],
  facets: [facet('type', 'Field type', (item) => Object.values(item.fields))],
}

export function tableFilters(definitions, connections) {
  return {
    searchFields: [
      name,
      search('table', 'Schema and table', (item) => [item.schemaName, item.tableName]),
      search('connection', 'Connection', (item) => definitionName(connections, item.connectionId)),
      search('fields', 'Fields definition, keys and types', (item) => [
        definitionName(definitions, item.fieldDefinitionId),
        Object.entries(item.columns),
      ]),
    ],
    facets: [
      facet(
        'connection',
        'Connection',
        (item) => item.connectionId,
        definitionOptions(connections),
      ),
      facet('type', 'Column type', (item) => Object.values(item.columns)),
    ],
  }
}

export function normalizeFilters(definitions) {
  return {
    searchFields: [
      ruleName,
      ruleDescription,
      search('fields', 'Fields and source/destination', (item) => [
        definitionName(definitions, item.spec.fieldDefinitionId),
        item.spec.sourceField,
        item.spec.destinationField,
      ]),
      search('condition', 'Condition', (item) => [
        item.spec.condition.replaceAll('_', ' '),
        item.spec.conditionValue,
      ]),
      search('action', 'Action', (item) => [
        item.spec.action.replaceAll('_', ' '),
        item.spec.actionValue,
        item.spec.replacementValue,
      ]),
    ],
    facets: [
      facet('action', 'Action type', (item) => item.spec.action),
      facet('condition', 'Condition type', (item) => item.spec.condition),
    ],
  }
}

function expressionDetails(node) {
  return node.type === 'GROUP'
    ? [node.junction, ...node.children.flatMap(expressionDetails)]
    : [
        node.field,
        node.operator.replaceAll('_', ' '),
        node.otherField,
        node.value == null ? '' : JSON.stringify(node.value),
      ]
}

export function conditionalFilters(definitions) {
  return {
    searchFields: [
      ruleName,
      ruleDescription,
      search('fields', 'Fields definition, keys and types', (item) => [
        definitionName(definitions, item.spec.fieldDefinitionId),
        Object.entries(item.referencedFields),
      ]),
      search('conditions', 'Conditions and values', (item) =>
        expressionDetails(item.spec.expression),
      ),
    ],
    facets: [facet('type', 'Compared field type', (item) => Object.values(item.referencedFields))],
  }
}

export function mapperFilters(definitions) {
  return {
    searchFields: [
      name,
      description,
      search('wasm', 'WASM directory and filename', (item) => [item.directory, item.wasmFileName]),
      search('outputs', 'Output names and Fields types', (item) =>
        item.outputs.map((output) => [
          output.name,
          definitionName(definitions, output.fieldDefinitionId),
          Object.entries(output.fields),
        ]),
      ),
    ],
    facets: [
      facet(
        'type',
        'Output Fields type',
        (item) => item.outputs.map((output) => output.fieldDefinitionId),
        definitionOptions(definitions),
      ),
    ],
  }
}

export function transformerFilters(definitions) {
  const ports = (item) => [...item.inputs, ...item.outputs]
  const details = (items) =>
    items.map((port) => [
      port.name,
      port.kind.replaceAll('_', ' '),
      definitionName(definitions, port.fieldDefinitionId),
      Object.entries(port.fields),
    ])
  return {
    searchFields: [
      name,
      description,
      search('wasm', 'WASM directory and filename', (item) => [item.directory, item.wasmFileName]),
      search('inputs', 'Inputs', (item) => details(item.inputs)),
      search('outputs', 'Outputs', (item) => details(item.outputs)),
    ],
    facets: [
      facet('kind', 'Port type', (item) => ports(item).map((port) => port.kind), [
        { value: 'FIELD_LIST', label: 'Fields list' },
        { value: 'DYNAMIC_VARIABLE', label: 'Dynamic variable' },
      ]),
      facet(
        'type',
        'Fields type',
        (item) =>
          ports(item)
            .filter((port) => port.fieldDefinitionId)
            .map((port) => port.fieldDefinitionId),
        definitionOptions(definitions),
      ),
    ],
  }
}
