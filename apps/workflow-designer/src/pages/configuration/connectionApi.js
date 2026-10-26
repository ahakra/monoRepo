export async function connectionApi(path = '', method = 'GET', form) {
  let body
  if (form) {
    const {
      name,
      type,
      password,
      host,
      port,
      database,
      username,
      sslMode,
      createDatabaseIfNotExists,
    } = form
    body = {
      name,
      type,
      password,
      settings: {
        host,
        port: String(port),
        database,
        username,
        sslMode,
        createDatabaseIfNotExists: String(!!createDatabaseIfNotExists),
      },
    }
  }
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
  return Array.isArray(result)
    ? result.map(({ settings, ...connection }) => ({
        ...connection,
        ...settings,
        createDatabaseIfNotExists: settings.createDatabaseIfNotExists === 'true',
      }))
    : result
}
