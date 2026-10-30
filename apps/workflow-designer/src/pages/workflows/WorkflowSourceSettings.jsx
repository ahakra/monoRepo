export default function WorkflowSourceSettings({ status, savedUpdatedAt }) {
  const source = status?.sourceConfiguration
  if (!source) return null
  const changed =
    status.running &&
    savedUpdatedAt &&
    new Date(savedUpdatedAt).getTime() > new Date(source.updatedAt).getTime()
  return (
    <details className="workflow-source-settings">
      <summary>
        {status.running ? 'Active' : 'Last run'} source settings · {source.name}
        {changed ? ' · newer settings saved' : ''}
      </summary>
      <p>
        Loaded when this run started. Saving a connector does not change an active consumer; Stop
        and Run loads the latest saved settings.
      </p>
      {changed && (
        <p role="status">
          This connector has changed since the run started. Stop and Run to apply the saved changes.
        </p>
      )}
      <dl>
        {[
          ['directoryName', 'Directory'],
          ['fileName', 'Exact filename / filename expression'],
          ['include', 'Include regex'],
          ['exclude', 'Exclude regex'],
          ['includeExt', 'Included extensions'],
          ['readLock', 'Read lock'],
        ].map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>
              <code>{source.settings[key] || '(not set)'}</code>
            </dd>
          </div>
        ))}
      </dl>
      <p>
        Connector saved: {new Date(source.updatedAt).toLocaleString()} · Run started:{' '}
        {new Date(status.startedAt).toLocaleString()}
      </p>
    </details>
  )
}
