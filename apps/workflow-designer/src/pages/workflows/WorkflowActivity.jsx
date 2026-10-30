import { Fragment, useState } from 'react'
import { activityMetrics, latestRun } from './workflowActivity.js'
import { batchExecutions, stageResults, stageStates, stageTypes } from './executionResults.js'
import StageResults from './StageResults.jsx'
import { useExecutionDetail } from './useExecutionDetail.js'

export default function WorkflowActivity({
  engine,
  onReveal,
  workflow,
  stageFilter,
  onStageFilter,
  logRequest = 0,
}) {
  const [runFilter, setRunFilter] = useState(workflow?.focusRunId || 'all')
  const [batchStatus, setBatchStatus] = useState('all')
  const [search, setSearch] = useState('')
  const [shown, setShown] = useState(50)
  const [expanded, setExpanded] = useState(
    workflow?.focusExchangeId ? `${workflow.focusRunId}:${workflow.focusExchangeId}` : null,
  )
  const runs = new Map()
  for (const event of engine.events)
    if (!runs.has(event.runId)) runs.set(event.runId, event.timestamp)
  for (const batch of engine.executions || [])
    if (!runs.has(batch.runId)) runs.set(batch.runId, batch.createdAt)
  const selectedRun =
    runFilter === 'latest'
      ? engine.status?.runId || engine.executions?.at(-1)?.runId || latestRun(engine.events)
      : runFilter
  const runEvents = engine.events.filter(
    (event) => runFilter === 'all' || event.runId === selectedRun,
  )
  const focused = useExecutionDetail(workflow?.id, workflow?.focusExecutionId)
  const loaded = engine.executions || batchExecutions(engine.events)
  const allBatches =
    focused.detail && !loaded.some((batch) => batch.executionId === workflow.focusExecutionId)
      ? [...loaded, focused.detail.batch].sort((a, b) => a.cursor - b.cursor)
      : loaded
  const batches = allBatches.filter((batch) => runFilter === 'all' || batch.runId === selectedRun)
  const filtered = batches
    .filter(
      (batch) =>
        (batchStatus === 'all' || batch.status === batchStatus) &&
        batch.description.toLowerCase().includes(search.toLowerCase()),
    )
    .reverse()
  const lifecycle = runEvents.filter((event) => !event.exchangeId)

  return (
    <section
      id="workflow-activity"
      className="workflow-activity"
      aria-labelledby="workflow-activity-title"
    >
      <header>
        <div>
          <h2 id="workflow-activity-title">Batches</h2>
          <p>One row per imported batch. Expand a batch to see its stages and logs.</p>
        </div>
        <span className="workflow-batch-count">
          {filtered.length} of {batches.length} batches
        </span>
      </header>
      {focused.error && (
        <p className="config-notice error" role="status">
          Unable to load selected execution: {focused.error}
        </p>
      )}
      {(engine.batchError || engine.traceError) && (
        <div className="config-notice error" role="status">
          Activity unavailable: {engine.batchError || engine.traceError}
          <button onClick={engine.retry}>Retry</button>
        </div>
      )}
      <div className="workflow-activity-filters">
        <label>
          Run
          <select
            value={runFilter}
            onChange={(event) => {
              setRunFilter(event.target.value)
              setExpanded(null)
            }}
          >
            <option value="all">All loaded runs</option>
            <option value="latest">Latest run</option>
            {[...runs].reverse().map(([id, timestamp]) => (
              <option key={id} value={id}>
                {new Date(timestamp).toLocaleString()} · {id.slice(0, 8)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select value={batchStatus} onChange={(event) => setBatchStatus(event.target.value)}>
            <option value="all">All statuses</option>
            <option value="running">Running</option>
            <option value="completed">Successful</option>
            <option value="failed">Failed</option>
            <option value="interrupted">Interrupted</option>
          </select>
        </label>
        <label className="workflow-activity-search">
          Batch description
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search filename or batch description"
          />
        </label>
      </div>
      <div className="workflow-batch-table">
        <table>
          <thead>
            <tr>
              <th>Batch description</th>
              <th>Source</th>
              <th>Created</th>
              <th>Status</th>
              <th>Duration</th>
              <th>
                <span className="workflow-sr-only">Details</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {filtered.slice(0, shown).map((batch) => {
              const open = expanded === batch.key
              const detailsId = `batch-${batch.key.replace(/[^a-zA-Z0-9_-]/g, '_')}`
              return (
                <Fragment key={batch.key}>
                  <tr
                    data-batch-id={batch.key}
                    className={`workflow-batch-row ${open ? 'is-expanded' : ''}`}
                  >
                    <td>
                      <strong>{batch.description}</strong>
                      {engine.status?.currentExchangeId === batch.id && (
                        <small>Current step: {engine.status.currentStep || 'Reading source'}</small>
                      )}
                    </td>
                    <td>
                      {batch.sourceType === 'file'
                        ? 'File'
                        : batch.sourceType === 'sql' || batch.sourceType === 'postgres'
                          ? 'SQL'
                          : batch.sourceType}
                    </td>
                    <td>
                      <time dateTime={batch.createdAt}>
                        {new Date(batch.createdAt).toLocaleString()}
                      </time>
                    </td>
                    <td>
                      <span className={`workflow-result-badge is-${batch.status}`}>
                        {stageStates[batch.status]}
                      </span>
                    </td>
                    <td>
                      {batch.durationMs == null
                        ? '—'
                        : batch.durationMs === 0
                          ? '<1 ms'
                          : `${batch.durationMs} ms`}
                    </td>
                    <td>
                      <button
                        aria-expanded={open}
                        aria-controls={detailsId}
                        aria-label={`${open ? 'Hide' : 'Show'} stages for ${batch.description}`}
                        onClick={() => setExpanded(open ? null : batch.key)}
                      >
                        {open ? 'Hide stages' : 'Show stages'}
                      </button>
                    </td>
                  </tr>
                  {open && (
                    <tr className="workflow-batch-detail-row">
                      <td colSpan={6}>
                        <div id={detailsId} className="workflow-batch-details">
                          <BatchDetails
                            key={`${batch.key}:${logRequest}`}
                            batch={batch}
                            workflow={workflow}
                            engine={engine}
                            onReveal={onReveal}
                            stageFilter={stageFilter}
                            onStageFilter={onStageFilter}
                          />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
      {!filtered.length && (
        <p>
          {batches.length
            ? 'No batches match these filters.'
            : 'No batches imported yet. Run the workflow and add an input file to its source folder.'}
        </p>
      )}
      {filtered.length > shown && (
        <button onClick={() => setShown((value) => value + 50)}>
          Show more batches ({filtered.length - shown} remaining)
        </button>
      )}
      {engine.hasMoreBatches && (
        <button
          disabled={engine.loadingMore}
          onClick={async () => {
            await engine.loadMoreBatches()
            setShown((value) => value + 50)
          }}
        >
          {engine.loadingMore ? 'Loading…' : 'Load older batches'}
        </button>
      )}
      {!!lifecycle.length && (
        <details className="workflow-lifecycle-logs">
          <summary>Workflow startup / stop logs ({lifecycle.length})</summary>
          <EventLog events={lifecycle} onReveal={onReveal} />
        </details>
      )}
      <details className="workflow-rerun-help">
        <summary>How do I run the same file again?</summary>
        <ol>
          <li>Stop the workflow.</li>
          <li>
            Move or delete old output files. Existing filenames are preserved and cause another
            write to fail.
          </li>
          <li>
            Move the input from its processed folder back to the source. The telecom demo uses{' '}
            <code>input/.processed/usage.csv</code>. Fix failed inputs before moving them back from{' '}
            <code>.invalid</code>.
          </li>
          <li>Click Run.</li>
        </ol>
        <p>
          Each file is a batch; its description is its filename. Batches, stage results, and logs
          are saved in PostgreSQL and remain available after a restart. Load older batches to browse
          more history.
        </p>
      </details>
    </section>
  )
}

function BatchDetails({ batch, workflow, engine, onReveal, stageFilter, onStageFilter }) {
  const [localStage, setLocalStage] = useState('all')
  const [level, setLevel] = useState('all')
  const [search, setSearch] = useState('')
  const [notice, setNotice] = useState('')
  const [showLogs, setShowLogs] = useState(!!stageFilter && stageFilter !== 'all')
  const selectedStage = stageFilter ?? localStage
  const setSelectedStage = onStageFilter || setLocalStage
  const saved = useExecutionDetail(workflow.id, batch.executionId, batch.updatedAt)
  const runEvents = engine.events.filter((event) => event.runId === batch.runId)
  const events = saved.detail?.events || runEvents.filter((event) => event.exchangeId === batch.id)
  const rows =
    saved.detail?.stages ||
    (batch.executionId ? [] : stageResults(runEvents, workflow, engine.status, batch.id))
  const activeStage = rows.some((row) => row.id === selectedStage) ? selectedStage : 'all'
  const filtered = events.filter(
    (event) =>
      (activeStage === 'all' ||
        event.nodeId === activeStage ||
        event.nodeIds?.includes(activeStage)) &&
      (level === 'all' || event.level === level) &&
      [event.step, event.stageType, event.message, event.type].some((value) =>
        value?.toLowerCase().includes(search.toLowerCase()),
      ),
  )
  const outputs = events.filter(
    (event) => event.type === 'OUTPUT_WRITTEN' && event.details?.outputPath,
  )
  const reveal = (id) =>
    onReveal?.(id, { runId: batch.runId, exchangeId: batch.id, executionId: batch.executionId })
  function exportLogs() {
    const url = URL.createObjectURL(
      new Blob([JSON.stringify({ batch, events: filtered }, null, 2)], {
        type: 'application/json',
      }),
    )
    const link = document.createElement('a')
    link.href = url
    link.download = 'batch-logs.json'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 0)
  }
  async function copyPath(path) {
    try {
      await navigator.clipboard.writeText(path)
      setNotice('Output path copied.')
    } catch {
      setNotice('Select the output path to copy it manually.')
    }
  }
  return (
    <>
      <header className="workflow-batch-detail-heading">
        <div>
          <h3>{batch.description}</h3>
          <p>Stages and logs for this batch only.</p>
        </div>
        <button onClick={exportLogs} disabled={!filtered.length}>
          Export batch logs
        </button>
      </header>
      {saved.error && (
        <p className="config-notice error" role="status">
          Unable to load saved stage logs: {saved.error}
        </p>
      )}
      {batch.executionId && !saved.detail && !saved.error && (
        <p role="status">Loading saved stage results…</p>
      )}
      <StageResults rows={rows} onReveal={onReveal ? reveal : undefined} />
      <details className="workflow-batch-output-list">
        <summary>Output files ({outputs.length})</summary>
        <div className="workflow-output-files">
          {!outputs.length ? (
            <p>No output files were written for this batch.</p>
          ) : (
            outputs.map((event) => (
              <article key={event.sequence}>
                <div>
                  <strong>{event.step || 'Output file'}</strong>
                  <code>{event.details.outputPath}</code>
                  <small>{activityMetrics(event)}</small>
                </div>
                <button onClick={() => copyPath(event.details.outputPath)}>Copy path</button>
                {onReveal && <button onClick={() => reveal(event.nodeId)}>Show step</button>}
              </article>
            ))
          )}
          {notice && <p role="status">{notice}</p>}
        </div>
      </details>
      <details
        className="workflow-batch-stage-logs"
        open={showLogs}
        onToggle={(event) => setShowLogs(event.currentTarget.open)}
      >
        <summary>Detailed stage logs ({events.length})</summary>
        <div className="workflow-activity-filters">
          <label>
            Stage
            <select value={activeStage} onChange={(event) => setSelectedStage(event.target.value)}>
              <option value="all">All stages</option>
              {rows.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Level
            <select value={level} onChange={(event) => setLevel(event.target.value)}>
              <option value="all">All events</option>
              <option value="ERROR">Errors</option>
              <option value="INFO">Information</option>
            </select>
          </label>
          <label className="workflow-activity-search">
            Search logs
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Stage or message"
            />
          </label>
        </div>
        <EventLog events={filtered} onReveal={onReveal ? reveal : undefined} />
      </details>
    </>
  )
}

function EventLog({ events, onReveal }) {
  const [shown, setShown] = useState(100)
  return (
    <div className="workflow-activity-events" role="log" aria-label="Stage logs" aria-live="off">
      {!events.length ? (
        <p>No logs match these filters.</p>
      ) : (
        <ol>
          {events
            .slice(-shown)
            .reverse()
            .map((event) => (
              <li key={event.sequence} className={event.level === 'ERROR' ? 'is-error' : ''}>
                <time dateTime={event.timestamp}>
                  {new Date(event.timestamp).toLocaleTimeString()}
                </time>
                <div>
                  <div className="workflow-event-heading">
                    <b>{event.type.replaceAll('_', ' ').toLowerCase()}</b>
                    {event.nodeId &&
                      (onReveal ? (
                        <button onClick={() => onReveal(event.nodeId)}>
                          {event.step || event.nodeId}
                        </button>
                      ) : (
                        <span>{event.step || event.nodeId}</span>
                      ))}
                    {event.stageType && (
                      <span>{stageTypes[event.stageType] || event.stageType}</span>
                    )}
                  </div>
                  <p>{event.message}</p>
                  {activityMetrics(event) && <small>{activityMetrics(event)}</small>}
                  <details>
                    <summary>Event details</summary>
                    <pre>{JSON.stringify(event, null, 2)}</pre>
                  </details>
                </div>
              </li>
            ))}
        </ol>
      )}
      {events.length > shown && (
        <button onClick={() => setShown((value) => value + 100)}>
          Show more logs ({events.length - shown} remaining)
        </button>
      )}
    </div>
  )
}
