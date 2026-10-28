import assert from 'node:assert/strict'
import test from 'node:test'
import { filterItems, initialFilters, facetOptions } from '../src/components/items/itemFilters.js'
import {
  connectionFilters,
  fieldFilters,
  normalizeFilters,
  transformerFilters,
} from '../src/components/items/itemFilterConfigs.js'

const items = [
  {
    id: 'a',
    name: 'Customer import',
    description: 'Parse CDR records',
    type: 'file',
    createdAt: '2026-10-27T12:00:00Z',
    updatedAt: '2026-10-28T12:00:00Z',
  },
  {
    id: 'b',
    name: 'Billing',
    description: 'PostgreSQL customer records',
    type: 'sql',
    createdAt: '2026-10-28T12:00:00Z',
    updatedAt: '2026-10-27T12:00:00Z',
  },
  {
    id: 'legacy',
    name: 'Legacy',
    description: 'Old record',
    type: 'sql',
    createdAt: null,
    updatedAt: '2026-10-26T12:00:00Z',
  },
]
const config = {
  searchFields: ['name', 'description'].map((id) => ({
    id,
    label: id,
    values: (item) => item[id],
  })),
  facets: [{ id: 'type', label: 'Type', values: (item) => item.type }],
}
const search = (changes, data = items, options = config) =>
  filterItems(data, { ...initialFilters(), ...changes }, options)

test('combines case-insensitive search terms, selected fields, and exact type facets', () => {
  assert.deepEqual(
    search({ query: 'CUSTOMER records' }).map((item) => item.id),
    ['a', 'b'],
  )
  assert.deepEqual(
    search({ query: 'customer', searchField: 'name' }).map((item) => item.id),
    ['a'],
  )
  assert.deepEqual(
    search({ query: 'customer', searchField: 'description', facets: { type: 'sql' } }).map(
      (item) => item.id,
    ),
    ['b'],
  )
  assert.equal(search({ query: '  ' }).length, 3)
  assert.equal(search({ query: 'missing' }).length, 0)
})

test('sorts by name and dates without mutating items, keeping unknown timestamps last', () => {
  const before = [...items]
  assert.deepEqual(
    search({ sort: 'created-desc' }).map((item) => item.id),
    ['b', 'a', 'legacy'],
  )
  assert.deepEqual(
    search({ sort: 'created-asc' }).map((item) => item.id),
    ['a', 'b', 'legacy'],
  )
  assert.deepEqual(
    search({ sort: 'name-asc' }).map((item) => item.id),
    ['b', 'a', 'legacy'],
  )
  assert.deepEqual(items, before)
})

test('creation-date ranges include the full local end day and exclude unknown creation times', () => {
  const data = [
    { id: 'start', createdAt: new Date(2026, 9, 28, 0, 0, 0).toISOString() },
    { id: 'end', createdAt: new Date(2026, 9, 28, 23, 59, 59, 999).toISOString() },
    { id: 'next', createdAt: new Date(2026, 9, 29, 0, 0, 0).toISOString() },
    { id: 'unknown', createdAt: null },
  ]
  const ids = search({ createdFrom: '2026-10-28', createdTo: '2026-10-28' }, data)
    .map((item) => item.id)
    .sort()
  assert.deepEqual(ids, ['end', 'start'])
  assert.equal(search({ createdFrom: '2026-10-29', createdTo: '2026-10-28' }, data).length, 0)
})

test('derives distinct facet options and uses exact values for filtering', () => {
  assert.deepEqual(
    facetOptions(items, config.facets[0]).map((option) => option.value),
    ['file', 'sql'],
  )
  assert.deepEqual(search({ facets: { type: 'SQL' } }), [])
})

test('searches only public connector details and supports field-type membership', () => {
  const connectors = [
    {
      id: 'db',
      name: 'Analytics',
      type: 'postgres',
      host: 'db.internal',
      database: 'warehouse',
      password: 'secret',
    },
  ]
  assert.equal(search({ query: 'warehouse' }, connectors, connectionFilters).length, 1)
  assert.equal(search({ query: 'secret' }, connectors, connectionFilters).length, 0)
  const definitions = [
    { id: 'fields', name: 'Calls', fields: { caller: 'string', duration: 'integer' } },
  ]
  assert.equal(
    search({ query: 'duration', facets: { type: 'integer' } }, definitions, fieldFilters).length,
    1,
  )
  assert.equal(search({ facets: { type: 'boolean' } }, definitions, fieldFilters).length, 0)
})

test('searches rule descriptions and actions and transformer input/output kinds', () => {
  const rules = [
    {
      id: 'rule',
      spec: {
        name: 'Local phone',
        description: 'Country cleanup',
        sourceField: 'phone',
        destinationField: 'local',
        fieldDefinitionId: 'fields',
        condition: 'STARTS_WITH',
        conditionValue: '+961',
        action: 'REMOVE_PREFIX',
        actionValue: '+961',
      },
    },
  ]
  assert.equal(
    search(
      { query: 'country', searchField: 'description', facets: { action: 'REMOVE_PREFIX' } },
      rules,
      normalizeFilters([]),
    ).length,
    1,
  )
  const transformers = [
    {
      id: 't',
      name: 'Enrich',
      description: 'Join calls',
      directory: '/opt/modules',
      wasmFileName: 'enrich.wasm',
      inputs: [
        {
          name: 'Calls',
          kind: 'FIELD_LIST',
          fieldDefinitionId: 'fields',
          fields: { caller: 'string' },
        },
      ],
      outputs: [{ name: 'count', kind: 'DYNAMIC_VARIABLE', fieldDefinitionId: null, fields: {} }],
    },
  ]
  assert.equal(
    search(
      { query: 'count', facets: { kind: 'DYNAMIC_VARIABLE' } },
      transformers,
      transformerFilters([]),
    ).length,
    1,
  )
  assert.equal(
    search(
      { query: 'enrich.wasm', searchField: 'wasm', facets: { type: 'fields' } },
      transformers,
      transformerFilters([]),
    ).length,
    1,
  )
})
