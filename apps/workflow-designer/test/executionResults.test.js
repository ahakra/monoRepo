import assert from 'node:assert/strict'
import test from 'node:test'
import {
  fileExecutions,
  batchExecutions,
  stageResults,
  stageCounts,
  stageDuration,
} from '../src/pages/workflows/executionResults.js'
import { readLocation } from '../src/pageLocation.js'

const nodes = [
  { id: 'source', name: 'Original source', type: 'dataSource' },
  { id: 'mapper', name: 'Parse usage', type: 'mapper' },
  { id: 'n1', name: 'Trim calling', type: 'normalizeRule' },
  { id: 'n2', name: 'Prefix calling', type: 'normalizeRule' },
  { id: 'writer', name: 'Write CDR', type: 'dataDestination' },
]
const make = (type, nodeIds, details = {}, exchangeId = 'file-1', stageType) => ({
  type,
  nodeIds,
  nodeId: nodeIds[0],
  step: nodeIds[0] ? nodes.find((node) => node.id === nodeIds[0]).name : null,
  runId: 'run-1',
  exchangeId,
  stageType,
  fileName: 'usage.csv',
  timestamp: '2026-10-30T10:00:00Z',
  details,
})
const events = [
  make('PLAN_READY', [], { nodes }, null),
  make('FILE_PICKED', ['source'], {}, 'file-1', 'dataSource'),
  make('FILE_READ', ['source'], { bytes: 900, durationMs: 1 }, 'file-1', 'dataSource'),
  make(
    'STEP_COMPLETED',
    ['mapper'],
    { outputs: { 'list:usage': 5 }, durationMs: 3 },
    'file-1',
    'mapper',
  ),
  make(
    'STEP_STARTED',
    ['n1', 'n2'],
    { nodeLabels: { n1: 'Trim calling', n2: 'Prefix calling' } },
    'file-1',
    'normalizeChain',
  ),
  make('NORMALIZATION_COMPLETED', ['n1'], { matched: 5, total: 5 }, 'file-1', 'normalizeRule'),
  make('NORMALIZATION_COMPLETED', ['n2'], { matched: 0, total: 5 }, 'file-1', 'normalizeRule'),
  make(
    'STEP_COMPLETED',
    ['n1', 'n2'],
    {
      outputs: { out: 5 },
      durationMs: 4,
      nodeLabels: { n1: 'Trim calling', n2: 'Prefix calling' },
    },
    'file-1',
    'normalizeChain',
  ),
  make(
    'OUTPUT_WRITTEN',
    ['writer'],
    { records: 5, bytes: 500, outputPath: '/tmp/cdr.csv', durationMs: 2 },
    'file-1',
    'dataDestination',
  ),
  make('FILE_COMPLETED', ['source'], { durationMs: 20 }, 'file-1', 'dataSource'),
  make('FILE_PICKED', ['source'], {}, 'file-2', 'dataSource'),
  { ...make('FILE_FAILED', ['mapper'], {}, 'file-2', 'mapper'), message: 'Invalid integer' },
]

test('stage results keep snapshot names/types and individual outcomes for grouped rules', () => {
  const workflow = {
    nodes: nodes.map((node) => ({ id: node.id, type: node.type, data: { label: 'Edited later' } })),
  }
  const rows = stageResults(events, workflow, { phase: 'WAITING' }, 'file-1')
  assert.equal(rows.length, 5)
  assert.equal(rows[0].name, 'Original source')
  assert.equal(rows[0].durationMs, 1)
  assert.equal(rows.find((row) => row.id === 'n2').name, 'Prefix calling')
  for (const row of rows.filter((row) => row.type === 'normalizeRule')) {
    assert.equal(row.status, 'completed')
    assert.equal(row.chainDurationMs, 4)
    assert.equal(row.durationMs, undefined)
    assert.equal(stageDuration(row), '4 ms (chain)')
  }
  assert.match(stageCounts(rows.find((row) => row.id === 'n2')), /0 \/ 5 matched/)
  assert.equal(rows.find((row) => row.id === 'writer').outputPath, '/tmp/cdr.csv')
})

test('files stay separate and downstream stages remain unreached after a failure', () => {
  const files = fileExecutions(events)
  assert.deepEqual(
    files.map((file) => file.status),
    ['completed', 'failed'],
  )
  const rows = stageResults(events, {}, { phase: 'WAITING' })
  assert.equal(rows.find((row) => row.id === 'mapper').status, 'failed')
  assert.equal(rows.find((row) => row.id === 'mapper').error, 'Invalid integer')
  assert.equal(rows.find((row) => row.id === 'writer').status, 'pending')
  const historic = stageResults(
    events,
    {},
    { phase: 'PROCESSING', currentExchangeId: 'file-2', currentNodeIds: ['writer'] },
    'file-1',
  )
  assert.equal(historic.find((row) => row.id === 'writer').status, 'completed')
  assert.equal(
    stageResults([events[0]], {}, {}).every((row) => row.status === 'awaiting'),
    true,
  )
})

test('navigation preserves workflow, stage and execution query parameters', () => {
  const location = readLocation('#workflows?workflow=one&node=mapper&run=run-1&file=file-1')
  assert.equal(location.page, 'workflows')
  assert.equal(new URLSearchParams(location.search).get('file'), 'file-1')
  assert.deepEqual(readLocation('#executions'), { page: 'executions', search: '' })
})

test('batch descriptions, creation times and outcomes stay separate for repeated files and future SQL batches', () => {
  const created = '2026-10-30T09:00:00Z'
  const retainedLater = {
    ...make('STEP_STARTED', ['mapper']),
    timestamp: '2026-10-30T09:00:10Z',
    details: { batchDescription: 'usage.csv', batchCreatedAt: created, sourceType: 'file' },
  }
  const completed = {
    ...retainedLater,
    type: 'FILE_COMPLETED',
    details: { ...retainedLater.details, durationMs: 20 },
  }
  const nextRun = { ...make('FILE_FAILED', ['mapper']), runId: 'run-2', details: { durationMs: 5 } }
  const sql = {
    ...make('BATCH_COMPLETED', ['mapper']),
    runId: 'sql-run',
    exchangeId: 'sql-batch',
    fileName: null,
    details: { sourceType: 'sql', lastImportedId: 0, batchCreatedAt: created, durationMs: 50 },
  }
  const batches = batchExecutions([retainedLater, completed, nextRun, sql])
  assert.equal(batches.length, 3)
  assert.equal(batches[0].description, 'usage.csv')
  assert.equal(batches[0].createdAt, created)
  assert.equal(batches[0].durationMs, 20)
  assert.equal(batches[0].status, 'completed')
  assert.equal(batches[1].status, 'failed')
  assert.notEqual(batches[0].key, batches[1].key)
  assert.equal(batches[2].description, 'Last imported ID: 0')
  assert.equal(batches[2].sourceType, 'sql')
  assert.equal(batches[2].status, 'completed')
})
