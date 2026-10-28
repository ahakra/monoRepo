export async function rulesApi(path = '', method = 'GET', body) {
  const response = await fetch(`/api/rules/normalize${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail || 'Unable to complete the rule request. Please try again.')
  }
  return response.status === 204 ? null : response.json()
}
