export async function mappersApi(path = '', method = 'GET', body) {
  const response = await fetch(`/api/mappers${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail || 'Unable to complete the mapper request. Please try again.')
  }
  return response.status === 204 ? null : response.json()
}
