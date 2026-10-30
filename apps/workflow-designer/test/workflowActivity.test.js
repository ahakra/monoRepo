import assert from 'node:assert/strict'
import test from 'node:test'
import {
  latestRun,
  nodeActivity,
  activityMetrics,
} from '../src/pages/workflows/workflowActivity.js'

test('canvas states follow the latest run and source file, including fused rules and failures', () => {
  const event = (sequence, runId, exchangeId, type, nodeIds) => ({
    sequence,
    runId,
    exchangeId,
    type,
    nodeIds,
  })
  const events = [
    event(1, 'old', 'old-file', 'STEP_COMPLETED', ['old-node']),
    event(2, 'current', 'first-file', 'STEP_FAILED', ['mapper']),
    event(3, 'current', 'second-file', 'STEP_STARTED', ['normalize-1', 'normalize-2']),
    event(4, 'current', 'second-file', 'NORMALIZATION_COMPLETED', ['normalize-1']),
    event(5, 'current', 'second-file', 'STEP_COMPLETED', ['normalize-1', 'normalize-2']),
    event(6, 'current', 'second-file', 'STEP_SKIPPED', ['empty-destination']),
    event(7, 'current', 'second-file', 'FILE_FAILED', ['writer']),
  ]
  assert.equal(latestRun(events), 'current')
  const states = nodeActivity(events, { phase: 'WAITING', runId: 'current' })
  assert.equal(states.has('old-node'), false)
  assert.equal(states.has('mapper'), false)
  assert.equal(states.get('normalize-1'), 'completed')
  assert.equal(states.get('normalize-2'), 'completed')
  assert.equal(states.get('empty-destination'), 'skipped')
  assert.equal(states.get('writer'), 'failed')
  assert.equal(
    nodeActivity(events, { phase: 'PROCESSING', currentNodeIds: ['next-step'] }).get('next-step'),
    'running',
  )
  assert.equal(
    nodeActivity([...events, event(8, 'new-run', null, 'WORKFLOW_STARTED', [])], {}).size,
    0,
  )
})

test('activity metrics describe port counts, rule matches, duration and written files', () => {
  assert.equal(
    activityMetrics({
      details: {
        inputs: { 'input:usage': 7 },
        outputs: { matched: 2, 'not-matched': 5 },
        durationMs: 0,
      },
    }),
    'In: usage: 7 · Out: matched: 2, not-matched: 5 · <1 ms',
  )
  assert.equal(activityMetrics({ details: { matched: 6, total: 7 } }), '6 / 7 matched')
  assert.match(
    activityMetrics({ details: { records: 2, bytes: 200, durationMs: 3 } }),
    /2 records · 200 bytes · 3 ms/,
  )
})
