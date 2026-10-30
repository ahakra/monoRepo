export async function workflowsApi(path = '', method = 'GET', body) {
  const response = await fetch(`/api/workflows${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail || 'Unable to complete the workflow request. Please try again.')
  }
  return response.status === 204 ? null : response.json()
}

export function workflowEngineApi(id, action = 'status') {
  if (!['status', 'start', 'stop'].includes(action)) throw new Error('Unknown workflow action.')
  const suffix = action === 'status' ? '' : `/${action}`
  return workflowsApi(
    `/${encodeURIComponent(id)}/engine${suffix}`,
    action === 'status' ? 'GET' : 'POST',
  )
}

export async function runWorkflow(id, saveBeforeRun) {
  const saved = saveBeforeRun ? await saveBeforeRun() : null
  return workflowEngineApi(saved?.id || id, 'start')
}

export function workflowEventsApi(id, after = 0) {
  return workflowsApi(`/${encodeURIComponent(id)}/engine/events?after=${after}&limit=2000`)
}

export function workflowBatchesApi(id, before) {
  return workflowsApi(
    `/${encodeURIComponent(id)}/engine/batches?limit=50${before == null ? '' : `&before=${before}`}`,
  )
}

export function workflowBatchApi(id, executionId) {
  return workflowsApi(
    `/${encodeURIComponent(id)}/engine/batches/${encodeURIComponent(executionId)}`,
  )
}
