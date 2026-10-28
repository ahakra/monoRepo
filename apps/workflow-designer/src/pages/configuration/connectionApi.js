import { connectionPayload, decodeConnection } from './connectionSettings.js'

export async function connectionApi(path = '', method = 'GET', form) {
  const body = form ? connectionPayload(form) : undefined
  const response = await fetch(`/api/connections${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(
      problem?.detail || 'Request failed. Check the configuration service and try again.',
    )
  }
  if (response.status === 204) return null
  const result = await response.json()
  return Array.isArray(result) ? result.map(decodeConnection) : result
}
