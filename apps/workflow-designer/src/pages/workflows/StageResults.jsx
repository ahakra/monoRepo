import { stageCounts, stageDuration, stageStates, stageTypes } from './executionResults.js'

export default function StageResults({ rows, onReveal }) {
  return (
    <section className="workflow-stage-results" aria-label="Stage results">
      <header>
        <h3>Stage results</h3>
        <span>
          {rows.filter((row) => row.status === 'completed').length} successful ·{' '}
          {rows.filter((row) => row.status === 'failed').length} failed ·{' '}
          {rows.filter((row) => row.status === 'skipped').length} skipped
        </span>
      </header>
      <div className="workflow-stage-table">
        <table>
          <thead>
            <tr>
              <th>Stage name</th>
              <th>Type</th>
              <th>Result</th>
              <th>Counts</th>
              <th>Duration</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className={row.status === 'failed' ? 'is-error' : ''}>
                <td>
                  {onReveal ? (
                    <button onClick={() => onReveal(row.id)}>{row.name}</button>
                  ) : (
                    row.name
                  )}
                </td>
                <td>{stageTypes[row.type] || row.type || 'Stage'}</td>
                <td>
                  <span className={`workflow-result-badge is-${row.status}`}>
                    {stageStates[row.status] || row.status}
                  </span>
                </td>
                <td>{stageCounts(row)}</td>
                <td>{stageDuration(row)}</td>
                <td>
                  {row.error ||
                    row.outputPath ||
                    row.message ||
                    'No event recorded for this stage.'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!rows.length && <p>No stage results recorded yet.</p>}
      {rows.some((row) => row.chainDurationMs != null) && (
        <p className="workflow-activity-help">
          Grouped normalization rules share one pass; their duration is measured for the chain.
        </p>
      )}
    </section>
  )
}

export function SelectedStageResult({ row, onLogs }) {
  if (!row) return null
  return (
    <section className="workflow-selected-result" aria-label="Selected stage result">
      <h3>Execution result</h3>
      <p>
        <strong>{row.name}</strong> · {stageTypes[row.type] || row.type}
      </p>
      <span className={`workflow-result-badge is-${row.status}`}>{stageStates[row.status]}</span>
      <p>{stageCounts(row)}</p>
      <p>Duration: {stageDuration(row)}</p>
      <p>{row.error || row.outputPath || row.message || 'No event recorded yet.'}</p>
      <button onClick={onLogs}>View stage logs</button>
    </section>
  )
}
