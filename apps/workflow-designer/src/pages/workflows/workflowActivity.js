export function latestRun(events) {
  return events.at(-1)?.runId || null
}

export function nodeActivity(events, status) {
  const run = latestRun(events) || status?.runId
  const runEvents = events.filter((event) => event.runId === run)
  const exchange = runEvents.findLast((event) => event.exchangeId)?.exchangeId
  const states = new Map()
  for (const event of runEvents) {
    if (!exchange || event.exchangeId !== exchange) continue
    let state
    if (['STEP_STARTED', 'FILE_PICKED'].includes(event.type)) state = 'running'
    if (
      [
        'STEP_COMPLETED',
        'NORMALIZATION_COMPLETED',
        'OUTPUT_WRITTEN',
        'FILE_READ',
        'FILE_COMPLETED',
      ].includes(event.type)
    )
      state = 'completed'
    if (event.type === 'STEP_SKIPPED') state = 'skipped'
    if (['STEP_FAILED', 'FILE_FAILED'].includes(event.type)) state = 'failed'
    if (state)
      (event.nodeIds || [event.nodeId]).filter(Boolean).forEach((id) => states.set(id, state))
  }
  if (status?.phase === 'PROCESSING')
    (status.currentNodeIds || []).forEach((id) => states.set(id, 'running'))
  return states
}

export function activityMetrics(event) {
  const details = event.details || {}
  const result = []
  for (const [key, label] of [
    ['inputs', 'In'],
    ['outputs', 'Out'],
  ]) {
    if (details[key])
      result.push(
        `${label}: ${Object.entries(details[key])
          .map(([name, count]) => `${name.replace(/^(list|input|output):/, '')}: ${count}`)
          .join(', ')}`,
      )
  }
  if (details.matched !== undefined) result.push(`${details.matched} / ${details.total} matched`)
  if (details.records !== undefined) result.push(`${details.records} records`)
  if (details.bytes !== undefined) result.push(`${details.bytes.toLocaleString()} bytes`)
  if (details.durationMs !== undefined)
    result.push(details.durationMs === 0 ? '<1 ms' : `${details.durationMs} ms`)
  return result.join(' · ')
}
