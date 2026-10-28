export async function conditionalApi(path = '', method = 'GET', body, raw = false) {
  const response = await fetch(`/api/rules/conditional${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(
      problem?.detail || 'Unable to complete the Conditional rule request. Please try again.',
    )
  }
  return response.status === 204 ? null : response.json()
}
