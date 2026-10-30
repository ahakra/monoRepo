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
