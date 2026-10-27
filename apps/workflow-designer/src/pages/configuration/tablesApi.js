export async function tablesApi(path = '', method = 'GET', body) {
  const response = await fetch(`/api/tables${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(
      problem?.detail ||
        'Unable to complete the table request. Check the configuration service and try again.',
    )
  }
  return response.status === 204 ? null : response.json()
}
