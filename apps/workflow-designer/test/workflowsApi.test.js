import assert from 'node:assert/strict'
import test from 'node:test'
import { workflowsApi } from '../src/pages/workflows/workflowsApi.js'

test('workflow API uses JSON requests and handles saved documents, deletion, and errors', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  let response = { ok: true, status: 200, json: async () => [{ id: 'saved' }] }
  globalThis.fetch = async (...args) => {
    calls.push(args)
    return response
  }
  try {
    assert.deepEqual(await workflowsApi(), [{ id: 'saved' }])
    assert.equal(calls[0][0], '/api/workflows')
    assert.equal(calls[0][1].method, 'GET')
    const draft = { id: 'draft', name: 'Export' }
    response = { ok: true, status: 201, json: async () => draft }
    assert.deepEqual(await workflowsApi('', 'POST', draft), draft)
    assert.equal(calls[1][1].body, JSON.stringify(draft))
    await workflowsApi('/draft', 'PUT', draft)
    assert.equal(calls[2][0], '/api/workflows/draft')
    assert.equal(calls[2][1].method, 'PUT')
    response = { ok: true, status: 204 }
    assert.equal(await workflowsApi('/draft', 'DELETE'), null)
    response = { ok: false, status: 400, json: async () => ({ detail: 'Invalid arrow' }) }
    await assert.rejects(workflowsApi('', 'POST', draft), /Invalid arrow/)
    response = {
      ok: false,
      status: 502,
      json: async () => {
        throw new Error('Not JSON')
      },
    }
    await assert.rejects(workflowsApi(), /Unable to complete the workflow request/)
    globalThis.fetch = async () => {
      throw new Error('Network offline')
    }
    await assert.rejects(workflowsApi(), /Network offline/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('engine actions use the lifecycle API and save before starting', async () => {
  const { workflowEngineApi, runWorkflow } = await import('../src/pages/workflows/workflowsApi.js')
  const originalFetch = globalThis.fetch
  const calls = []
  const status = { workflowId: 'saved', running: true, successfulFiles: 2, failedFiles: 0 }
  globalThis.fetch = async (url, options) => {
    calls.push({ url, method: options.method })
    return { ok: true, status: 200, json: async () => status }
  }
  try {
    assert.deepEqual(await workflowEngineApi('saved'), status)
    assert.deepEqual(calls.pop(), { url: '/api/workflows/saved/engine', method: 'GET' })
    await workflowEngineApi('saved', 'stop')
    assert.deepEqual(calls.pop(), { url: '/api/workflows/saved/engine/stop', method: 'POST' })
    const events = []
    globalThis.fetch = async (url, options) => {
      events.push(`request:${url}:${options.method}`)
      return { ok: true, status: 200, json: async () => status }
    }
    await runWorkflow('draft', async () => {
      events.push('saved')
      return { id: 'saved' }
    })
    assert.deepEqual(events, ['saved', 'request:/api/workflows/saved/engine/start:POST'])
    events.length = 0
    await assert.rejects(
      runWorkflow('draft', async () => {
        throw new Error('Save failed')
      }),
      /Save failed/,
    )
    assert.deepEqual(events, [])
    await runWorkflow('saved')
    assert.deepEqual(events, ['request:/api/workflows/saved/engine/start:POST'])
    assert.throws(() => workflowEngineApi('saved', 'delete'), /Unknown workflow action/)
    globalThis.fetch = async () => ({
      ok: false,
      status: 400,
      json: async () => ({ detail: 'Connect all transformer inputs' }),
    })
    await assert.rejects(runWorkflow('saved'), /Connect all transformer inputs/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('workflow activity uses a workflow-scoped incremental cursor', async () => {
  const { workflowEventsApi } = await import('../src/pages/workflows/workflowsApi.js')
  const originalFetch = globalThis.fetch
  let requested
  globalThis.fetch = async (url) => {
    requested = url
    return { ok: true, status: 200, json: async () => ({ events: [], nextAfter: 42 }) }
  }
  try {
    assert.equal((await workflowEventsApi('saved', 42)).nextAfter, 42)
    assert.equal(requested, '/api/workflows/saved/engine/events?after=42&limit=2000')
  } finally {
    globalThis.fetch = originalFetch
  }
})
