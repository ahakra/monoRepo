export const stageTypes = {
  workflow: 'Workflow',
  dataSource: 'Data source',
  dataDestination: 'Data destination',
  mapper: 'Mapper',
  transformer: 'Transformer',
  normalizeRule: 'Normalize rule',
  normalizeChain: 'Normalization chain',
  conditionalRule: 'Conditional rule',
  step: 'Placeholder',
}
export const stageStates = {
  completed: 'Successful',
  failed: 'Failed',
  interrupted: 'Interrupted',
  running: 'Running',
  skipped: 'Skipped',
  pending: 'Not reached',
  awaiting: 'Not run',
}

export function batchExecutions(events) {
  const batches = new Map()
  for (const event of events) {
    if (!event.exchangeId) continue
    const details = event.details || {}
    const key = `${event.runId}:${event.exchangeId}`
    const description =
      details.batchDescription ??
      event.fileName ??
      (details.lastImportedId !== undefined && details.lastImportedId !== null
        ? `Last imported ID: ${details.lastImportedId}`
        : undefined)
    const batch = batches.get(key) || {
      key,
      id: event.exchangeId,
      runId: event.runId,
      description:
        description == null || description === '' ? 'Imported batch' : String(description),
      sourceType:
        details.sourceType ||
        (event.fileName ? 'file' : details.lastImportedId !== undefined ? 'sql' : 'batch'),
      createdAt: details.batchCreatedAt || event.timestamp,
      status: 'running',
    }
    if (description != null && description !== '') batch.description = String(description)
    if (details.sourceType) batch.sourceType = details.sourceType
    if (details.batchCreatedAt) batch.createdAt = details.batchCreatedAt
    if (['FILE_COMPLETED', 'BATCH_COMPLETED'].includes(event.type)) batch.status = 'completed'
    if (['FILE_FAILED', 'BATCH_FAILED'].includes(event.type)) batch.status = 'failed'
    if (event.type === 'BATCH_INTERRUPTED') batch.status = 'interrupted'
    if (
      ['FILE_COMPLETED', 'FILE_FAILED', 'BATCH_COMPLETED', 'BATCH_FAILED'].includes(event.type) &&
      details.durationMs !== undefined
    )
      batch.durationMs = details.durationMs
    batch.updatedAt = event.timestamp
    batches.set(key, batch)
  }
  return [...batches.values()]
}

// Keep the file-oriented adapter for existing canvas and execution-selection callers.
export function fileExecutions(events) {
  return batchExecutions(events).map((batch) => ({
    ...batch,
    name: batch.description,
    startedAt: batch.createdAt,
  }))
}

export function stageResults(events, workflow, status, exchangeId) {
  const runId = events.at(-1)?.runId || status?.runId
  const runEvents = events.filter((event) => event.runId === runId)
  const fileId = exchangeId || fileExecutions(runEvents).at(-1)?.id
  const plan = runEvents.findLast((event) => event.type === 'PLAN_READY')?.details?.nodes
  const nodes =
    plan ||
    (workflow?.nodes || []).map((node) => ({ id: node.id, name: node.data.label, type: node.type }))
  const rows = new Map(
    nodes.map((node) => [node.id, { ...node, status: fileId ? 'pending' : 'awaiting' }]),
  )
  for (const event of runEvents) {
    if (!fileId || event.exchangeId !== fileId) continue
    let state
    if (['STEP_STARTED', 'FILE_PICKED', 'BATCH_STARTED'].includes(event.type)) state = 'running'
    if (
      ['STEP_COMPLETED', 'NORMALIZATION_COMPLETED', 'OUTPUT_WRITTEN', 'FILE_READ'].includes(
        event.type,
      )
    )
      state = 'completed'
    if (event.type === 'STEP_SKIPPED') state = 'skipped'
    if (['STEP_FAILED', 'FILE_FAILED', 'BATCH_FAILED'].includes(event.type)) state = 'failed'
    if (!state) continue
    const details = event.details || {}
    const kind = event.stageType || details.stageType || details.kind
    for (const id of (event.nodeIds?.length ? event.nodeIds : [event.nodeId]).filter(Boolean)) {
      const row = rows.get(id) || {
        id,
        name: id,
        type: kind === 'normalizeChain' ? 'normalizeRule' : kind,
      }
      row.name =
        details.nodeLabels?.[id] ||
        (event.nodeId === id && event.step && kind !== 'normalizeChain' ? event.step : row.name)
      row.type = kind === 'normalizeChain' ? 'normalizeRule' : kind || row.type
      row.status = state
      row.message = event.message
      if (state === 'failed') row.error = event.message
      if (details.inputs) row.inputs = details.inputs
      if (details.outputs) row.outputs = details.outputs
      if (details.matched !== undefined) {
        row.matched = details.matched
        row.total = details.total
      }
      if (details.bytes !== undefined) row.bytes = details.bytes
      if (details.records !== undefined) row.records = details.records
      if (details.outputPath) row.outputPath = details.outputPath
      if (
        details.durationMs !== undefined &&
        !['FILE_FAILED', 'BATCH_FAILED'].includes(event.type)
      ) {
        if (kind === 'normalizeChain') row.chainDurationMs = details.durationMs
        else row.durationMs = details.durationMs
      }
      rows.set(id, row)
    }
  }
  if (status?.phase === 'PROCESSING' && (!fileId || fileId === status.currentExchangeId)) {
    for (const id of status.currentNodeIds || []) {
      const row = rows.get(id)
      if (row) row.status = 'running'
    }
  }
  return [...rows.values()]
}

export function stageCounts(row) {
  const values = []
  if (row.matched != null) values.push(`${row.matched} / ${row.total} matched`)
  for (const [key, label] of [
    ['inputs', 'In'],
    ['outputs', 'Out'],
  ]) {
    if (row[key])
      values.push(
        `${label}: ${Object.entries(row[key])
          .map(([port, count]) => `${port.replace(/^(list|input|output):/, '')} ${count}`)
          .join(', ')}`,
      )
  }
  if (row.records != null) values.push(`${row.records} records`)
  if (row.bytes != null) values.push(`${row.bytes.toLocaleString()} bytes`)
  return values.join(' · ') || '—'
}

export function stageDuration(row) {
  if (row.durationMs != null) return row.durationMs === 0 ? '<1 ms' : `${row.durationMs} ms`
  if (row.chainDurationMs != null)
    return `${row.chainDurationMs === 0 ? '<1' : row.chainDurationMs} ms (chain)`
  return '—'
}
